package com.emanstagram.comment;

import com.emanstagram.common.ApiException;
import com.emanstagram.common.Cursor;
import com.emanstagram.common.CursorPage;
import com.emanstagram.moderation.ReportRepository;
import com.emanstagram.notification.NotificationKind;
import com.emanstagram.notification.NotificationRepository;
import com.emanstagram.notification.NotificationService;
import com.emanstagram.post.Post;
import com.emanstagram.post.PostRepository;
import com.emanstagram.social.AccessPolicy;
import com.emanstagram.user.User;
import com.emanstagram.user.UserViews;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class CommentService {

    public record CommentResponse(
            UUID id,
            UUID postId,
            UUID parentId,
            UserSummary author,
            String body,
            int likeCount,
            boolean likedByMe,
            long replyCount,
            Instant createdAt,
            Instant editedAt
    ) {
    }

    public record CreateCommentRequest(
            @NotBlank(message = "Write something first")
            @Size(max = 1000, message = "Comments are limited to 1000 characters")
            String body,

            UUID parentId
    ) {
    }

    public record CommentLikeState(boolean liked, int likeCount) {
    }

    private final CommentRepository comments;
    private final CommentLikeRepository likes;
    private final PostRepository posts;
    private final NotificationRepository notificationRows;
    private final ReportRepository reports;
    private final NotificationService notifications;
    private final AccessPolicy access;
    private final UserViews userViews;

    public CommentService(CommentRepository comments, CommentLikeRepository likes,
                          PostRepository posts, NotificationRepository notificationRows,
                          ReportRepository reports, NotificationService notifications,
                          AccessPolicy access, UserViews userViews) {
        this.comments = comments;
        this.likes = likes;
        this.posts = posts;
        this.notificationRows = notificationRows;
        this.reports = reports;
        this.notifications = notifications;
        this.access = access;
        this.userViews = userViews;
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> topLevel(UUID postId, User viewer, String cursor, Integer limit) {
        visiblePost(postId, viewer);
        int size = CursorPage.clamp(limit == null ? 20 : limit);
        Cursor c = Cursor.decodeAscending(cursor);
        List<Comment> rows = comments.topLevelPage(postId, access.hiddenFrom(viewer.getId()),
                c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, CommentService::cursorOf, page -> assemble(page, viewer.getId()));
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> replies(UUID commentId, User viewer, String cursor, Integer limit) {
        Comment parent = comments.findById(commentId)
                .orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "That comment was deleted."));
        visiblePost(parent.getPostId(), viewer);
        int size = CursorPage.clamp(limit == null ? 20 : limit);
        Cursor c = Cursor.decodeAscending(cursor);
        List<Comment> rows = comments.repliesPage(commentId, access.hiddenFrom(viewer.getId()),
                c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, CommentService::cursorOf, page -> assemble(page, viewer.getId()));
    }

    @Transactional
    public CommentResponse create(UUID postId, User author, CreateCommentRequest request) {
        Post post = visiblePost(postId, author);

        UUID parentId = null;
        Comment parent = null;
        if (request.parentId() != null) {
            parent = comments.findWithAuthorById(request.parentId())
                    .filter(p -> p.getPostId().equals(postId))
                    .orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND",
                            "The comment you're replying to was deleted."));
            // Keep threads two levels deep: replying to a reply joins the root thread.
            parentId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
        }

        Comment saved = comments.save(new Comment(postId, author, parentId, request.body().trim()));
        posts.adjustCommentCount(postId, 1);

        Set<UUID> notified = new HashSet<>();
        notifications.notify(post.getAuthorId(), author, NotificationKind.COMMENT, postId, saved.getId(),
                saved.getBody());
        notified.add(post.getAuthorId());
        if (parent != null && !notified.contains(parent.getAuthorId())) {
            notifications.notify(parent.getAuthorId(), author, NotificationKind.COMMENT, postId, saved.getId(),
                    "replied: " + saved.getBody());
            notified.add(parent.getAuthorId());
        }
        notifications.notifyMentions(author, saved.getBody(), postId, saved.getId(), notified);

        return assemble(List.of(saved), author.getId()).get(0);
    }

    /** The comment's author, the post's author, or a moderator may delete a comment and its replies. */
    @Transactional
    public void delete(UUID commentId, User actor) {
        Comment comment = comments.findWithAuthorById(commentId)
                .orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "That comment was already deleted."));
        Post post = posts.findById(comment.getPostId()).orElseThrow(AccessPolicy::postNotFound);

        boolean allowed = comment.getAuthorId().equals(actor.getId())
                || post.getAuthorId().equals(actor.getId())
                || AccessPolicy.isModerator(actor);
        if (!allowed) {
            throw ApiException.forbidden("NOT_YOUR_COMMENT", "You can't delete this comment.");
        }
        deleteComment(comment);
    }

    /** Deletes a comment and its replies, keeping the post's counter in step. */
    @Transactional
    public void deleteComment(Comment comment) {
        List<UUID> ids = new ArrayList<>(comments.replyIds(comment.getId()));
        ids.add(comment.getId());

        likes.deleteByComments(ids);
        notificationRows.deleteByComments(ids);
        reports.deleteByComments(ids);
        comments.deleteAllByIdIn(ids);
        posts.adjustCommentCount(comment.getPostId(), -ids.size());
    }

    @Transactional
    public CommentLikeState like(UUID commentId, User user) {
        Comment comment = visibleComment(commentId, user);
        if (likes.insertIfAbsent(user.getId(), commentId) == 1) {
            comments.adjustLikeCount(commentId, 1);
            notifications.notify(comment.getAuthorId(), user, NotificationKind.LIKE,
                    comment.getPostId(), commentId, comment.getBody());
        }
        return new CommentLikeState(true, comments.likeCountOf(commentId).orElse(0));
    }

    @Transactional
    public CommentLikeState unlike(UUID commentId, User user) {
        Comment comment = visibleComment(commentId, user);
        if (likes.deleteLike(user.getId(), commentId) == 1) {
            comments.adjustLikeCount(commentId, -1);
            notifications.retract(comment.getAuthorId(), user.getId(), NotificationKind.LIKE,
                    comment.getPostId(), commentId);
        }
        return new CommentLikeState(false, comments.likeCountOf(commentId).orElse(0));
    }

    // ---------------- helpers ----------------

    private List<CommentResponse> assemble(List<Comment> rows, UUID viewerId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(Comment::getId).toList();
        Set<UUID> liked = likes.likedAmong(viewerId, ids);

        Map<UUID, Long> replyCounts = new HashMap<>();
        List<UUID> roots = rows.stream().filter(c -> c.getParentId() == null).map(Comment::getId).toList();
        if (!roots.isEmpty()) {
            for (Object[] row : comments.replyCounts(roots)) {
                replyCounts.put((UUID) row[0], (Long) row[1]);
            }
        }

        List<CommentResponse> result = new ArrayList<>(rows.size());
        for (Comment c : rows) {
            result.add(new CommentResponse(c.getId(), c.getPostId(), c.getParentId(),
                    userViews.summary(c.getAuthor()), c.getBody(), c.getLikeCount(),
                    liked.contains(c.getId()), replyCounts.getOrDefault(c.getId(), 0L),
                    c.getCreatedAt(), c.getEditedAt()));
        }
        return result;
    }

    private Post visiblePost(UUID postId, User viewer) {
        Post post = posts.findWithAuthorById(postId).orElseThrow(AccessPolicy::postNotFound);
        return access.requireVisible(post, viewer);
    }

    private Comment visibleComment(UUID commentId, User viewer) {
        Comment comment = comments.findWithAuthorById(commentId)
                .orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "That comment was deleted."));
        visiblePost(comment.getPostId(), viewer);
        return comment;
    }

    private static Cursor cursorOf(Comment c) {
        return new Cursor(c.getCreatedAt(), c.getId());
    }
}
