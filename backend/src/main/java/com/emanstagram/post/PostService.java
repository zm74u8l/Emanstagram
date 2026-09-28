package com.emanstagram.post;

import com.emanstagram.comment.CommentLikeRepository;
import com.emanstagram.comment.CommentRepository;
import com.emanstagram.common.AfterCommit;
import com.emanstagram.common.ApiException;
import com.emanstagram.common.Cursor;
import com.emanstagram.common.CursorPage;
import com.emanstagram.common.Times;
import com.emanstagram.moderation.ReportRepository;
import com.emanstagram.notification.NotificationKind;
import com.emanstagram.notification.NotificationRepository;
import com.emanstagram.notification.NotificationService;
import com.emanstagram.post.dto.PostDtos.*;
import com.emanstagram.social.AccessPolicy;
import com.emanstagram.storage.MediaValidationService;
import com.emanstagram.storage.SupabaseStorageService;
import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import com.emanstagram.user.UserViews;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository posts;
    private final PostLikeRepository likes;
    private final SavedPostRepository saves;
    private final CommentRepository comments;
    private final CommentLikeRepository commentLikes;
    private final NotificationRepository notificationRows;
    private final ReportRepository reports;
    private final UserRepository users;
    private final PostAssembler assembler;
    private final AccessPolicy access;
    private final MediaValidationService validation;
    private final SupabaseStorageService storage;
    private final NotificationService notifications;
    private final UserViews userViews;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public PostService(PostRepository posts, PostLikeRepository likes, SavedPostRepository saves,
                       CommentRepository comments, CommentLikeRepository commentLikes,
                       NotificationRepository notificationRows, ReportRepository reports,
                       UserRepository users, PostAssembler assembler, AccessPolicy access,
                       MediaValidationService validation, SupabaseStorageService storage,
                       NotificationService notifications, UserViews userViews,
                       ObjectMapper json, PlatformTransactionManager txManager) {
        this.posts = posts;
        this.likes = likes;
        this.saves = saves;
        this.comments = comments;
        this.commentLikes = commentLikes;
        this.notificationRows = notificationRows;
        this.reports = reports;
        this.users = users;
        this.assembler = assembler;
        this.access = access;
        this.validation = validation;
        this.storage = storage;
        this.notifications = notifications;
        this.userViews = userViews;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------

    /**
     * Creates a post with one to ten media items.
     *
     * <p>Files are validated first, then uploaded, then the rows are written in
     * one transaction. Uploading happens outside the transaction so a slow
     * upload never holds a database connection; if anything after the first
     * upload fails, the objects already stored are deleted so nothing is
     * orphaned in the bucket.
     */
    public PostResponse create(User author, List<MultipartFile> files, String metaJson,
                               String caption, String location, PostVisibility visibility) {
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("FILE_MISSING", "Add at least one photo or video.");
        }
        validation.validateCarouselCount(files.size());
        validation.validateCaption(caption);
        if (location != null && location.length() > 160) {
            throw ApiException.badRequest("LOCATION_TOO_LONG", "Location is limited to 160 characters.");
        }
        for (MultipartFile file : files) {
            if (validation.isVideo(file.getContentType())) {
                validation.validateVideo(file);
            } else {
                validation.validateImage(file);
            }
        }
        List<MediaMeta> meta = parseMeta(metaJson, files.size());
        storage.requireConfigured();

        List<String> uploaded = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                uploaded.add(storage.upload(SupabaseStorageService.POSTS, author.getId(), file.getBytes(),
                        file.getContentType(), validation.extensionFor(file.getContentType())));
            }

            return tx.execute(status -> {
                PostKind kind = files.size() > 1 ? PostKind.CAROUSEL
                        : validation.isVideo(files.get(0).getContentType()) ? PostKind.VIDEO : PostKind.IMAGE;
                Post post = new Post(author, blankToNull(caption), kind,
                        visibility == null ? PostVisibility.PUBLIC : visibility, blankToNull(location));
                for (int i = 0; i < files.size(); i++) {
                    MultipartFile file = files.get(i);
                    MediaMeta m = meta.get(i);
                    post.addMedia(new PostMedia(i, uploaded.get(i), file.getContentType(), file.getSize(),
                            positive(m.width()), positive(m.height()), positive(m.durationMs()),
                            trimBlurhash(m.blurhash())));
                }
                post = posts.save(post);
                users.adjustPostCount(author.getId(), 1);
                notifications.notifyMentions(author, caption, post.getId(), null, Set.of());
                return assembler.assemble(post, author.getId());
            });
        } catch (IOException ex) {
            storage.deleteAll(uploaded);
            throw ApiException.badRequest("UPLOAD_UNREADABLE", "That file could not be read. Please try again.");
        } catch (RuntimeException ex) {
            storage.deleteAll(uploaded);
            throw ex;
        }
    }

    // ------------------------------------------------------------------
    // read / update / delete
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PostResponse get(UUID id, User viewer) {
        Post post = posts.findWithAuthorById(id).orElseThrow(AccessPolicy::postNotFound);
        access.requireVisible(post, viewer);
        return assembler.assemble(post, viewer.getId());
    }

    @Transactional
    public PostResponse update(UUID id, User editor, UpdatePostRequest request) {
        Post post = posts.findWithAuthorById(id).orElseThrow(AccessPolicy::postNotFound);
        if (!post.getAuthorId().equals(editor.getId())) {
            throw ApiException.forbidden("NOT_YOUR_POST", "You can only edit your own posts.");
        }
        if (request.caption() != null && !Objects.equals(request.caption(), post.getCaption())) {
            post.setCaption(blankToNull(request.caption()));
            post.setEditedAt(Times.now());
        }
        if (request.location() != null) {
            post.setLocation(blankToNull(request.location()));
        }
        if (request.visibility() != null) {
            post.setVisibility(request.visibility());
        }
        return assembler.assemble(post, editor.getId());
    }

    /** The author, or a moderator, may delete a post. */
    @Transactional
    public void delete(UUID id, User actor) {
        Post post = posts.findWithAuthorById(id).orElseThrow(AccessPolicy::postNotFound);
        if (!post.getAuthorId().equals(actor.getId()) && !AccessPolicy.isModerator(actor)) {
            throw ApiException.forbidden("NOT_YOUR_POST", "You can only delete your own posts.");
        }
        deletePost(post);
    }

    /**
     * Removes a post and everything hanging off it.
     *
     * <p>Postgres would cascade most of this through foreign keys, but the H2
     * profile's generated schema has no cascades, so the dependants are
     * deleted explicitly. That keeps the two databases behaving the same.
     */
    @Transactional
    public void deletePost(Post post) {
        UUID postId = post.getId();
        UUID authorId = post.getAuthorId();
        List<String> keys = post.getMedia().stream().map(PostMedia::getStorageKey).toList();

        List<UUID> commentIds = comments.idsForPost(postId);
        if (!commentIds.isEmpty()) {
            commentLikes.deleteByComments(commentIds);
            notificationRows.deleteByComments(commentIds);
            reports.deleteByComments(commentIds);
            comments.deleteAllByIdIn(commentIds);
        }
        likes.deleteByPost(postId);
        saves.deleteByPost(postId);
        notificationRows.deleteByPost(postId);
        reports.deleteByPost(postId);
        posts.delete(post);
        posts.flush();
        users.adjustPostCount(authorId, -1);

        AfterCommit.run(() -> storage.deleteAll(keys));
        log.info("Deleted post {} ({} media)", postId, keys.size());
    }

    // ------------------------------------------------------------------
    // likes and saves (idempotent PUT/DELETE)
    // ------------------------------------------------------------------

    @Transactional
    public LikeState like(UUID postId, User user) {
        Post post = visible(postId, user);
        if (likes.insertIfAbsent(user.getId(), postId) == 1) {
            posts.adjustLikeCount(postId, 1);
            notifications.notify(post.getAuthorId(), user, NotificationKind.LIKE, postId, null, null);
        }
        return new LikeState(true, posts.likeCountOf(postId).orElse(0));
    }

    @Transactional
    public LikeState unlike(UUID postId, User user) {
        Post post = visible(postId, user);
        if (likes.deleteLike(user.getId(), postId) == 1) {
            posts.adjustLikeCount(postId, -1);
            notifications.retract(post.getAuthorId(), user.getId(), NotificationKind.LIKE, postId, null);
        }
        return new LikeState(false, posts.likeCountOf(postId).orElse(0));
    }

    @Transactional
    public SaveState save(UUID postId, User user) {
        visible(postId, user);
        saves.insertIfAbsent(user.getId(), postId);
        return new SaveState(true);
    }

    @Transactional
    public SaveState unsave(UUID postId, User user) {
        // No visibility check: you can always remove something from your own
        // collection, even after its author blocked you.
        saves.deleteSave(user.getId(), postId);
        return new SaveState(false);
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> likers(UUID postId, User viewer, String cursor, Integer limit) {
        visible(postId, viewer);
        int size = CursorPage.clamp(limit);
        Cursor c = Cursor.decodeDescending(cursor);
        Set<UUID> hidden = access.hiddenFrom(viewer.getId());
        List<PostLike> rows = likes.likersPage(postId, c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size,
                l -> new Cursor(l.getCreatedAt(), l.getUserId()),
                page -> {
                    Map<UUID, User> byId = userViews.load(page.stream().map(PostLike::getUserId).toList());
                    List<User> ordered = page.stream().map(l -> byId.get(l.getUserId()))
                            .filter(u -> u != null && !hidden.contains(u.getId())).toList();
                    return userViews.summariesWithFollowing(ordered, viewer.getId());
                });
    }

    // ------------------------------------------------------------------
    // lists
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> byUser(String username, User viewer, String cursor, Integer limit) {
        User author = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "That account doesn't exist."));
        if (!author.getId().equals(viewer.getId()) && access.blockedEitherWay(author.getId(), viewer.getId())) {
            return new CursorPage<>(List.of(), null);
        }
        int size = CursorPage.clamp(limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<Post> rows = posts.byAuthor(author.getId(), access.visibleOn(author.getId(), viewer.getId()),
                c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, PostService::cursorOf, page -> assembler.assemble(page, viewer.getId()));
    }

    /** The viewer's Saved collection, newest save first. Posts they can no longer see drop out. */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> saved(User viewer, String cursor, Integer limit) {
        int size = CursorPage.clamp(limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<SavedPost> rows = saves.savedPage(viewer.getId(), c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size,
                s -> new Cursor(s.getCreatedAt(), s.getPostId()),
                page -> {
                    Map<UUID, Post> byId = new HashMap<>();
                    posts.findAllWithAuthorByIdIn(page.stream().map(SavedPost::getPostId).toList())
                            .forEach(p -> byId.put(p.getId(), p));
                    List<Post> ordered = page.stream().map(s -> byId.get(s.getPostId()))
                            .filter(p -> p != null && access.canView(p, viewer)).toList();
                    return assembler.assemble(ordered, viewer.getId());
                });
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    public static Cursor cursorOf(Post p) {
        return new Cursor(p.getCreatedAt(), p.getId());
    }

    private Post visible(UUID postId, User viewer) {
        Post post = posts.findWithAuthorById(postId).orElseThrow(AccessPolicy::postNotFound);
        return access.requireVisible(post, viewer);
    }

    private List<MediaMeta> parseMeta(String metaJson, int count) {
        List<MediaMeta> meta = new ArrayList<>();
        if (metaJson != null && !metaJson.isBlank()) {
            try {
                meta.addAll(json.readValue(metaJson, new TypeReference<List<MediaMeta>>() { }));
            } catch (IOException ex) {
                throw ApiException.badRequest("INVALID_META", "The upload details were malformed.");
            }
        }
        // Metadata is optional polish (dimensions, blurhash). Missing entries
        // are padded rather than rejected.
        while (meta.size() < count) {
            meta.add(new MediaMeta(null, null, null, null));
        }
        return meta;
    }

    private static Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static String trimBlurhash(String hash) {
        if (hash == null || hash.isBlank() || hash.length() > 200) {
            return null;
        }
        return hash;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
