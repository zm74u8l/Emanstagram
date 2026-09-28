package com.emanstagram.notification;

import com.emanstagram.common.Cursor;
import com.emanstagram.common.CursorPage;
import com.emanstagram.common.TextTokens;
import com.emanstagram.post.PostMedia;
import com.emanstagram.post.PostMediaRepository;
import com.emanstagram.realtime.RealtimePublisher;
import com.emanstagram.social.BlockRepository;
import com.emanstagram.social.FollowRepository;
import com.emanstagram.storage.StorageService;
import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import com.emanstagram.user.UserViews;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Creates, lists and pushes activity notifications. */
@Service
public class NotificationService {

    /** Caps how many people one caption can ping, so a pasted list can't spam. */
    private static final int MAX_MENTIONS = 10;

    public record NotificationResponse(
            UUID id,
            NotificationKind kind,
            UserSummary actor,
            UUID postId,
            String postThumbUrl,
            UUID commentId,
            String message,
            boolean read,
            Instant createdAt
    ) {
    }

    public record UnreadCount(long count) {
    }

    private final NotificationRepository notifications;
    private final BlockRepository blocks;
    private final FollowRepository follows;
    private final UserRepository users;
    private final PostMediaRepository media;
    private final UserViews userViews;
    private final StorageService storage;
    private final RealtimePublisher realtime;

    public NotificationService(NotificationRepository notifications, BlockRepository blocks,
                               FollowRepository follows, UserRepository users,
                               PostMediaRepository media, UserViews userViews,
                               StorageService storage, RealtimePublisher realtime) {
        this.notifications = notifications;
        this.blocks = blocks;
        this.follows = follows;
        this.users = users;
        this.media = media;
        this.userViews = userViews;
        this.storage = storage;
        this.realtime = realtime;
    }

    /**
     * Records a notification and pushes it live.
     *
     * <p>Skipped when the actor is the recipient or a block exists between
     * them. LIKE and FOLLOW are collapsed, so liking, unliking and re-liking
     * produces one entry rather than three.
     */
    @Transactional
    public void notify(UUID recipientId, User actor, NotificationKind kind,
                       UUID postId, UUID commentId, String message) {
        if (recipientId.equals(actor.getId()) || blocks.existsEitherWay(recipientId, actor.getId())) {
            return;
        }
        if ((kind == NotificationKind.LIKE || kind == NotificationKind.FOLLOW)
                && notifications.existsSame(recipientId, actor.getId(), kind, postId, commentId)) {
            return;
        }
        Notification saved = notifications.save(
                new Notification(recipientId, actor, kind, postId, commentId, excerpt(message)));

        // Built inside the transaction; only the send waits for commit.
        NotificationResponse payload = assemble(List.of(saved), recipientId).get(0);
        realtime.toUser(recipientId, "notification", payload);
    }

    /** Removes the notification for a like or follow that has been undone. */
    @Transactional
    public void retract(UUID recipientId, UUID actorId, NotificationKind kind, UUID postId, UUID commentId) {
        notifications.retract(recipientId, actorId, kind, postId, commentId);
    }

    /**
     * Notifies everyone @mentioned in {@code text}, except {@code alreadyNotified}
     * (e.g. the post author, who already got a COMMENT notification).
     */
    @Transactional
    public void notifyMentions(User actor, String text, UUID postId, UUID commentId,
                               Collection<UUID> alreadyNotified) {
        Set<String> names = TextTokens.mentions(text);
        int sent = 0;
        for (String name : names) {
            if (sent >= MAX_MENTIONS) {
                break;
            }
            Optional<User> target = users.findByUsernameIgnoreCase(name);
            if (target.isEmpty() || alreadyNotified.contains(target.get().getId())) {
                continue;
            }
            notify(target.get().getId(), actor, NotificationKind.MENTION, postId, commentId, text);
            sent++;
        }
    }

    @Transactional(readOnly = true)
    public CursorPage<NotificationResponse> page(UUID me, String cursor, Integer limit) {
        int size = CursorPage.clamp(limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<Notification> rows = notifications.page(me, c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size,
                n -> new Cursor(n.getCreatedAt(), n.getId()),
                page -> assemble(page, me));
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(UUID me) {
        return new UnreadCount(notifications.countByRecipientIdAndReadFalse(me));
    }

    @Transactional
    public void markAllRead(UUID me) {
        notifications.markAllRead(me);
        realtime.toUser(me, "notifications.read", Map.of());
    }

    private List<NotificationResponse> assemble(List<Notification> rows, UUID viewer) {
        Set<UUID> postIds = new HashSet<>();
        List<User> actors = new ArrayList<>();
        for (Notification n : rows) {
            if (n.getPostId() != null) {
                postIds.add(n.getPostId());
            }
            if (n.getActor() != null) {
                actors.add(n.getActor());
            }
        }

        Map<UUID, String> thumbs = new HashMap<>();
        if (!postIds.isEmpty()) {
            for (PostMedia m : media.findForPosts(postIds)) {
                thumbs.putIfAbsent(m.getPost().getId(), storage.publicUrl(m.getStorageKey()));
            }
        }

        // Follow-back buttons need to know whom the viewer already follows.
        Set<UUID> actorIds = new HashSet<>();
        actors.forEach(a -> actorIds.add(a.getId()));
        Set<UUID> followed = actorIds.isEmpty() ? Set.of() : follows.followedAmong(viewer, actorIds);

        List<NotificationResponse> result = new ArrayList<>(rows.size());
        for (Notification n : rows) {
            UserSummary actor = n.getActor() == null ? null
                    : userViews.summary(n.getActor()).withFollowing(followed.contains(n.getActor().getId()));
            result.add(new NotificationResponse(
                    n.getId(), n.getKind(), actor, n.getPostId(),
                    n.getPostId() == null ? null : thumbs.get(n.getPostId()),
                    n.getCommentId(), n.getMessage(), n.isRead(), n.getCreatedAt()));
        }
        return result;
    }

    private static String excerpt(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 140 ? flat : flat.substring(0, 139) + "…";
    }
}
