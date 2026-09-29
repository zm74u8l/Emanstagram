package com.emanstagram.moderation;

import com.emanstagram.common.ApiException;
import com.emanstagram.common.Times;
import com.emanstagram.post.Post;
import com.emanstagram.post.PostRepository;
import com.emanstagram.post.PostService;
import com.emanstagram.social.SocialService;
import com.emanstagram.story.StoryService;
import com.emanstagram.user.RefreshTokenRepository;
import com.emanstagram.user.Role;
import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import com.emanstagram.user.UserViews;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Moderator tools for accounts: find the heaviest or newest accounts, and
 * suspend someone (optionally wiping their posts and stories, which is how
 * storage taken by an abuser is reclaimed).
 */
@Service
public class AccountModerationService {

    private static final Logger log = LoggerFactory.getLogger(AccountModerationService.class);
    private static final int MAX_LIST = 100;

    public enum Sort { STORAGE, RECENT, SUSPENDED }

    public record AccountView(
            UserSummary user,
            String email,
            Role role,
            Instant createdAt,
            int postCount,
            int followerCount,
            long storageBytes,
            Instant suspendedAt,
            String suspendedReason
    ) {
    }

    public record SuspendRequest(
            @Size(max = 300, message = "Keep the reason under 300 characters")
            String reason,
            boolean deleteContent
    ) {
    }

    public record SuspendResult(int postsDeleted, int storiesDeleted) {
    }

    private final EntityManager em;
    private final UserRepository users;
    private final PostRepository posts;
    private final PostService postService;
    private final StoryService storyService;
    private final RefreshTokenRepository refreshTokens;
    private final UserViews userViews;

    public AccountModerationService(EntityManager em, UserRepository users, PostRepository posts,
                                    PostService postService, StoryService storyService,
                                    RefreshTokenRepository refreshTokens, UserViews userViews) {
        this.em = em;
        this.users = users;
        this.posts = posts;
        this.postService = postService;
        this.storyService = storyService;
        this.refreshTokens = refreshTokens;
        this.userViews = userViews;
    }

    /**
     * Accounts with how much storage each uses. Storage is summed across
     * posts, stories, chat attachments, avatar and banner.
     */
    @Transactional(readOnly = true)
    public List<AccountView> list(Sort sort, String query, Integer limit) {
        int size = Math.min(Math.max(limit == null ? 30 : limit, 1), MAX_LIST);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String order = switch (sort) {
            case STORAGE -> "used DESC, u.created_at DESC";
            case RECENT -> "u.created_at DESC";
            case SUSPENDED -> "u.suspended_at DESC";
        };
        // Native SQL: summing across four tables per user is awkward in JPQL.
        // Only the ORDER BY is chosen from a fixed enum; every value is bound.
        String sql = """
                SELECT u.id,
                       COALESCE((SELECT SUM(m.size_bytes) FROM post_media m JOIN posts p ON p.id = m.post_id
                                 WHERE p.author_id = u.id), 0)
                     + COALESCE((SELECT SUM(s.size_bytes) FROM stories s WHERE s.author_id = u.id), 0)
                     + COALESCE((SELECT SUM(x.attachment_bytes) FROM messages x
                                 WHERE x.sender_id = u.id AND x.deleted_at IS NULL), 0)
                     + u.avatar_bytes + u.banner_bytes AS used
                FROM users u
                WHERE (:q = '' OR lower(u.username) LIKE :pattern OR lower(u.email) LIKE :pattern)
                  AND (:suspendedOnly = FALSE OR u.suspended_at IS NOT NULL)
                ORDER BY %s
                """.formatted(order);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("q", q)
                .setParameter("pattern", "%" + q.replace("%", "").replace("_", "") + "%")
                .setParameter("suspendedOnly", sort == Sort.SUSPENDED)
                .setMaxResults(size)
                .getResultList();

        List<UUID> ids = new ArrayList<>();
        Map<UUID, Long> used = new HashMap<>();
        for (Object[] row : rows) {
            // Postgres returns a UUID; H2 returns the raw 16 bytes.
            UUID id = switch (row[0]) {
                case UUID u -> u;
                case byte[] b -> {
                    var buf = java.nio.ByteBuffer.wrap(b);
                    yield new UUID(buf.getLong(), buf.getLong());
                }
                default -> UUID.fromString(row[0].toString());
            };
            ids.add(id);
            used.put(id, ((Number) row[1]).longValue());
        }
        Map<UUID, User> byId = userViews.load(ids);
        List<AccountView> result = new ArrayList<>();
        for (UUID id : ids) {
            User u = byId.get(id);
            if (u != null) {
                result.add(new AccountView(userViews.summary(u), u.getEmail(), u.getRole(), u.getCreatedAt(),
                        u.getPostCount(), u.getFollowerCount(), used.get(id), u.getSuspendedAt(),
                        u.getSuspendedReason()));
            }
        }
        return result;
    }

    /**
     * Suspends an account: it can't sign in, refresh a session, use the API
     * or open a socket, and every session it has is revoked immediately.
     */
    @Transactional
    public SuspendResult suspend(UUID targetId, SuspendRequest request, User moderator) {
        User target = users.findById(targetId).orElseThrow(SocialService::userNotFound);
        requireAuthority(target, moderator);

        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        target.suspend(Times.now(), reason);
        users.saveAndFlush(target);
        refreshTokens.revokeAllForUser(targetId);

        int postsDeleted = 0;
        int storiesDeleted = 0;
        if (request.deleteContent()) {
            for (Post post : posts.findAllByAuthorId(targetId)) {
                postService.deletePost(post);
                postsDeleted++;
            }
            storiesDeleted = storyService.deleteAllBy(targetId);
        }
        log.info("{} suspended {} (reason: {}, posts deleted: {}, stories deleted: {})",
                moderator.getUsername(), target.getUsername(), reason, postsDeleted, storiesDeleted);
        return new SuspendResult(postsDeleted, storiesDeleted);
    }

    @Transactional
    public void unsuspend(UUID targetId, User moderator) {
        User target = users.findById(targetId).orElseThrow(SocialService::userNotFound);
        requireAuthority(target, moderator);
        target.unsuspend();
        log.info("{} lifted the suspension on {}", moderator.getUsername(), target.getUsername());
    }

    /**
     * Moderators can act on ordinary users; admins also on moderators.
     * Nobody can act on themselves or on an admin.
     */
    private static void requireAuthority(User target, User moderator) {
        if (target.getId().equals(moderator.getId())) {
            throw ApiException.badRequest("SELF_ACTION", "You can't suspend yourself.");
        }
        boolean allowed = target.getRole() == Role.USER
                || (target.getRole() == Role.MODERATOR && moderator.getRole() == Role.ADMIN);
        if (!allowed) {
            throw ApiException.forbidden("INSUFFICIENT_ROLE", "You can't suspend this account.");
        }
    }
}
