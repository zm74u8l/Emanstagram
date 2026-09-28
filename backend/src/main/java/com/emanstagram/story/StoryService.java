package com.emanstagram.story;

import com.emanstagram.common.AfterCommit;
import com.emanstagram.common.ApiException;
import com.emanstagram.common.Times;
import com.emanstagram.social.AccessPolicy;
import com.emanstagram.social.FollowRepository;
import com.emanstagram.social.SocialService;
import com.emanstagram.storage.MediaValidationService;
import com.emanstagram.storage.SupabaseStorageService;
import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import com.emanstagram.user.UserViews;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** 24-hour stories: the tray, the viewer, and who has seen what. */
@Service
public class StoryService {

    public static final Duration LIFETIME = Duration.ofHours(24);
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public record StoryItem(
            UUID id,
            String url,
            String mimeType,
            String caption,
            String backgroundHex,
            Instant createdAt,
            Instant expiresAt,
            boolean viewed,
            /** Only present on your own stories. */
            Long viewCount
    ) {
    }

    public record StoryGroup(UserSummary user, List<StoryItem> stories, boolean hasUnseen, Instant latestAt) {
    }

    public record StoryViewer(UserSummary user, Instant viewedAt) {
    }

    private final StoryRepository stories;
    private final StoryViewRepository views;
    private final FollowRepository follows;
    private final UserRepository users;
    private final UserViews userViews;
    private final AccessPolicy access;
    private final SupabaseStorageService storage;
    private final MediaValidationService validation;
    private final TransactionTemplate tx;

    public StoryService(StoryRepository stories, StoryViewRepository views, FollowRepository follows,
                        UserRepository users, UserViews userViews, AccessPolicy access,
                        SupabaseStorageService storage, MediaValidationService validation,
                        PlatformTransactionManager txManager) {
        this.stories = stories;
        this.views = views;
        this.follows = follows;
        this.users = users;
        this.userViews = userViews;
        this.access = access;
        this.storage = storage;
        this.validation = validation;
        this.tx = new TransactionTemplate(txManager);
    }

    public StoryItem create(User me, MultipartFile file, String caption, String backgroundHex) {
        boolean video = validation.isVideo(file == null ? null : file.getContentType());
        if (video) {
            validation.validateStoryVideo(file);
        } else {
            validation.validateImage(file);
        }
        if (caption != null && caption.length() > 300) {
            throw ApiException.badRequest("CAPTION_TOO_LONG", "Story captions are limited to 300 characters.");
        }
        String background = backgroundHex != null && HEX.matcher(backgroundHex).matches() ? backgroundHex : null;

        String key;
        try {
            key = storage.upload(SupabaseStorageService.STORIES, me.getId(), file.getBytes(),
                    file.getContentType(), validation.extensionFor(file.getContentType()));
        } catch (IOException ex) {
            throw ApiException.badRequest("UPLOAD_UNREADABLE", "That file could not be read. Please try again.");
        }
        try {
            return tx.execute(status -> {
                Instant now = Times.now();
                Story s = stories.save(new Story(me, key, file.getContentType(),
                        caption == null || caption.isBlank() ? null : caption.trim(), background, now.plus(LIFETIME)));
                return toItem(s, false, 0L);
            });
        } catch (RuntimeException ex) {
            storage.delete(key);
            throw ex;
        }
    }

    /**
     * The story tray: your own stories first, then people you follow with
     * something unseen (most recent first), then those you've watched.
     */
    @Transactional(readOnly = true)
    public List<StoryGroup> tray(User me) {
        Set<UUID> authors = new LinkedHashSet<>();
        authors.add(me.getId());
        authors.addAll(follows.followeeIds(me.getId()));
        authors.removeAll(access.hiddenFrom(me.getId()));

        List<StoryGroup> groups = groupsFor(stories.activeByAuthors(authors, Times.now()), me.getId());
        groups.sort(Comparator
                .comparing((StoryGroup g) -> !g.user().id().equals(me.getId()))
                .thenComparing(g -> !g.hasUnseen())
                .thenComparing(StoryGroup::latestAt, Comparator.reverseOrder()));
        return groups;
    }

    /** One person's active stories, e.g. when tapping their avatar on a profile. */
    @Transactional(readOnly = true)
    public StoryGroup forUser(String username, User me) {
        User author = users.findByUsernameIgnoreCase(username).orElseThrow(SocialService::userNotFound);
        if (!author.getId().equals(me.getId()) && access.blockedEitherWay(author.getId(), me.getId())) {
            throw SocialService.userNotFound();
        }
        List<StoryGroup> groups = groupsFor(stories.activeByAuthors(List.of(author.getId()), Times.now()), me.getId());
        if (groups.isEmpty()) {
            throw ApiException.notFound("NO_STORIES", "No stories right now.");
        }
        return groups.get(0);
    }

    @Transactional
    public void markViewed(UUID storyId, User me) {
        Story s = visible(storyId, me);
        if (!s.getAuthorId().equals(me.getId())) {
            views.insertIfAbsent(storyId, me.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<StoryViewer> viewers(UUID storyId, User me) {
        Story s = stories.findById(storyId).orElseThrow(StoryService::storyNotFound);
        if (!s.getAuthorId().equals(me.getId())) {
            throw ApiException.forbidden("NOT_YOUR_STORY", "Only the author can see who viewed a story.");
        }
        List<StoryView> rows = views.findByStoryIdOrderByViewedAtDesc(storyId);
        Map<UUID, User> byId = userViews.load(rows.stream().map(StoryView::getUserId).toList());
        List<StoryViewer> result = new ArrayList<>();
        for (StoryView v : rows) {
            User u = byId.get(v.getUserId());
            if (u != null) {
                result.add(new StoryViewer(userViews.summary(u), v.getViewedAt()));
            }
        }
        return result;
    }

    @Transactional
    public void delete(UUID storyId, User me) {
        Story s = stories.findById(storyId).orElseThrow(StoryService::storyNotFound);
        if (!s.getAuthorId().equals(me.getId()) && !AccessPolicy.isModerator(me)) {
            throw ApiException.forbidden("NOT_YOUR_STORY", "You can only delete your own stories.");
        }
        removeAll(List.of(s));
    }

    /** Deletes expired stories and their files. Called by {@link StoryCleanupJob}. */
    @Transactional
    public int purgeExpired() {
        List<Story> expired = stories.findByExpiresAtBefore(Times.now());
        removeAll(expired);
        return expired.size();
    }

    // ---------------- helpers ----------------

    private void removeAll(List<Story> list) {
        if (list.isEmpty()) {
            return;
        }
        List<UUID> ids = list.stream().map(Story::getId).toList();
        List<String> keys = list.stream().map(Story::getStorageKey).toList();
        views.deleteByStories(ids);
        stories.deleteAll(list);
        AfterCommit.run(() -> storage.deleteAll(keys));
    }

    private List<StoryGroup> groupsFor(List<Story> active, UUID viewerId) {
        if (active.isEmpty()) {
            return new ArrayList<>();
        }
        List<UUID> ids = active.stream().map(Story::getId).toList();
        Set<UUID> seen = views.viewedAmong(viewerId, ids);
        Map<UUID, Long> counts = new HashMap<>();
        List<UUID> own = active.stream().filter(s -> s.getAuthorId().equals(viewerId)).map(Story::getId).toList();
        if (!own.isEmpty()) {
            for (Object[] row : views.viewCounts(own)) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
        }

        Map<UUID, List<Story>> byAuthor = new LinkedHashMap<>();
        active.forEach(s -> byAuthor.computeIfAbsent(s.getAuthorId(), k -> new ArrayList<>()).add(s));

        List<StoryGroup> groups = new ArrayList<>();
        byAuthor.forEach((authorId, list) -> {
            boolean mine = authorId.equals(viewerId);
            List<StoryItem> items = list.stream()
                    .map(s -> toItem(s, mine || seen.contains(s.getId()), mine ? counts.getOrDefault(s.getId(), 0L) : null))
                    .toList();
            boolean unseen = items.stream().anyMatch(i -> !i.viewed());
            Instant latest = list.get(list.size() - 1).getCreatedAt();
            groups.add(new StoryGroup(userViews.summary(list.get(0).getAuthor()), items, unseen, latest));
        });
        return groups;
    }

    private StoryItem toItem(Story s, boolean viewed, Long viewCount) {
        return new StoryItem(s.getId(), storage.publicUrl(s.getStorageKey()), s.getMimeType(), s.getCaption(),
                s.getBackgroundHex(), s.getCreatedAt(), s.getExpiresAt(), viewed, viewCount);
    }

    private Story visible(UUID storyId, User me) {
        Story s = stories.findById(storyId)
                .filter(x -> x.getExpiresAt().isAfter(Times.now()))
                .orElseThrow(StoryService::storyNotFound);
        if (!s.getAuthorId().equals(me.getId()) && access.blockedEitherWay(s.getAuthorId(), me.getId())) {
            throw storyNotFound();
        }
        return s;
    }

    private static ApiException storyNotFound() {
        return ApiException.notFound("STORY_NOT_FOUND", "This story is no longer available.");
    }
}
