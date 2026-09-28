package com.emanstagram.social;

import com.emanstagram.common.ApiException;
import com.emanstagram.common.Cursor;
import com.emanstagram.common.CursorPage;
import com.emanstagram.notification.NotificationKind;
import com.emanstagram.notification.NotificationService;
import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import com.emanstagram.user.UserViews;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;

/** Follows and blocks. */
@Service
public class SocialService {

    public record FollowState(boolean following, int followerCount) {
    }

    public record BlockState(boolean blocked) {
    }

    private final FollowRepository follows;
    private final BlockRepository blocks;
    private final UserRepository users;
    private final UserViews userViews;
    private final AccessPolicy access;
    private final NotificationService notifications;

    public SocialService(FollowRepository follows, BlockRepository blocks, UserRepository users,
                         UserViews userViews, AccessPolicy access, NotificationService notifications) {
        this.follows = follows;
        this.blocks = blocks;
        this.users = users;
        this.userViews = userViews;
        this.access = access;
        this.notifications = notifications;
    }

    @Transactional
    public FollowState follow(UUID targetId, User me) {
        User target = requireOther(targetId, me, "You can't follow yourself.");
        if (access.blockedEitherWay(me.getId(), targetId)) {
            throw ApiException.forbidden("BLOCKED", "You can't follow this account.");
        }
        if (follows.insertIfAbsent(me.getId(), targetId) == 1) {
            users.adjustFollowingCount(me.getId(), 1);
            users.adjustFollowerCount(targetId, 1);
            notifications.notify(targetId, me, NotificationKind.FOLLOW, null, null, null);
        }
        return new FollowState(true, followerCount(target.getId()));
    }

    @Transactional
    public FollowState unfollow(UUID targetId, User me) {
        requireOther(targetId, me, "You can't unfollow yourself.");
        if (follows.deleteEdge(me.getId(), targetId) == 1) {
            users.adjustFollowingCount(me.getId(), -1);
            users.adjustFollowerCount(targetId, -1);
            notifications.retract(targetId, me.getId(), NotificationKind.FOLLOW, null, null);
        }
        return new FollowState(false, followerCount(targetId));
    }

    /** Removes someone from your followers without blocking them. */
    @Transactional
    public void removeFollower(UUID followerId, User me) {
        if (follows.deleteEdge(followerId, me.getId()) == 1) {
            users.adjustFollowingCount(followerId, -1);
            users.adjustFollowerCount(me.getId(), -1);
        }
    }

    /**
     * Blocks a user and severs any follow in either direction, so a blocked
     * person immediately drops out of feeds and follower lists.
     */
    @Transactional
    public BlockState block(UUID targetId, User me) {
        requireOther(targetId, me, "You can't block yourself.");
        blocks.insertIfAbsent(me.getId(), targetId);
        if (follows.deleteEdge(me.getId(), targetId) == 1) {
            users.adjustFollowingCount(me.getId(), -1);
            users.adjustFollowerCount(targetId, -1);
        }
        if (follows.deleteEdge(targetId, me.getId()) == 1) {
            users.adjustFollowingCount(targetId, -1);
            users.adjustFollowerCount(me.getId(), -1);
        }
        return new BlockState(true);
    }

    @Transactional
    public BlockState unblock(UUID targetId, User me) {
        blocks.deleteEdge(me.getId(), targetId);
        return new BlockState(false);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> blocked(User me) {
        List<Block> rows = blocks.findByBlockerIdOrderByCreatedAtDesc(me.getId());
        Map<UUID, User> byId = userViews.load(rows.stream().map(Block::getBlockedId).toList());
        return rows.stream().map(b -> byId.get(b.getBlockedId())).filter(Objects::nonNull)
                .map(userViews::summary).toList();
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> followers(String username, User viewer, String cursor, Integer limit) {
        User user = visibleUser(username, viewer);
        int size = CursorPage.clamp(limit == null ? 20 : limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<Follow> rows = follows.followersPage(user.getId(), c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, f -> new Cursor(f.getCreatedAt(), f.getFollowerId()),
                page -> summaries(page, Follow::getFollowerId, viewer));
    }

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> following(String username, User viewer, String cursor, Integer limit) {
        User user = visibleUser(username, viewer);
        int size = CursorPage.clamp(limit == null ? 20 : limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<Follow> rows = follows.followingPage(user.getId(), c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, f -> new Cursor(f.getCreatedAt(), f.getFolloweeId()),
                page -> summaries(page, Follow::getFolloweeId, viewer));
    }

    /** Popular accounts the viewer doesn't follow yet, for an empty feed and the sidebar. */
    @Transactional(readOnly = true)
    public List<UserSummary> suggested(User viewer, Integer limit) {
        int size = Math.min(limit == null ? 8 : Math.max(limit, 1), 30);
        List<User> rows = users.suggestions(viewer.getId(), access.hiddenFrom(viewer.getId()),
                PageRequest.of(0, size));
        return userViews.summariesWithFollowing(rows, viewer.getId());
    }

    // ---------------- helpers ----------------

    private List<UserSummary> summaries(List<Follow> page, Function<Follow, UUID> side, User viewer) {
        Set<UUID> hidden = access.hiddenFrom(viewer.getId());
        Map<UUID, User> byId = userViews.load(page.stream().map(side).toList());
        List<User> ordered = page.stream().map(f -> byId.get(side.apply(f)))
                .filter(u -> u != null && !hidden.contains(u.getId())).toList();
        return userViews.summariesWithFollowing(ordered, viewer.getId());
    }

    private User visibleUser(String username, User viewer) {
        User user = users.findByUsernameIgnoreCase(username).orElseThrow(SocialService::userNotFound);
        if (!user.getId().equals(viewer.getId()) && blocks.existsByBlockerIdAndBlockedId(user.getId(), viewer.getId())) {
            throw userNotFound();
        }
        return user;
    }

    private User requireOther(UUID targetId, User me, String selfMessage) {
        if (targetId.equals(me.getId())) {
            throw ApiException.badRequest("SELF_ACTION", selfMessage);
        }
        return users.findById(targetId).orElseThrow(SocialService::userNotFound);
    }

    private int followerCount(UUID userId) {
        return users.findById(userId).map(User::getFollowerCount).orElse(0);
    }

    public static ApiException userNotFound() {
        return ApiException.notFound("USER_NOT_FOUND", "That account doesn't exist.");
    }
}
