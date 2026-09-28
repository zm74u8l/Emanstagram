package com.emanstagram.user;

import com.emanstagram.social.FollowRepository;
import com.emanstagram.storage.SupabaseStorageService;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Turns {@link User} entities into the compact {@link UserSummary} that every
 * other response embeds, resolving avatar keys to CDN URLs in one place.
 */
@Component
public class UserViews {

    private final SupabaseStorageService storage;
    private final FollowRepository follows;
    private final UserRepository users;

    public UserViews(SupabaseStorageService storage, FollowRepository follows, UserRepository users) {
        this.storage = storage;
        this.follows = follows;
        this.users = users;
    }

    public UserSummary summary(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.effectiveName(),
                storage.publicUrl(user.getAvatarKey()),
                user.isVerified(),
                null);
    }

    /**
     * Summaries with {@code following} filled in for {@code viewerId}, using a
     * single query for the whole list.
     */
    public List<UserSummary> summariesWithFollowing(Collection<User> list, UUID viewerId) {
        Set<UUID> ids = new HashSet<>();
        list.forEach(u -> ids.add(u.getId()));
        Set<UUID> followed = ids.isEmpty() ? Set.of() : follows.followedAmong(viewerId, ids);
        List<UserSummary> result = new ArrayList<>(list.size());
        for (User u : list) {
            result.add(summary(u).withFollowing(followed.contains(u.getId())));
        }
        return result;
    }

    /** Loads users by id in one query, keyed for lookup while assembling a page. */
    public Map<UUID, User> load(Collection<UUID> ids) {
        Map<UUID, User> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        users.findAllById(new HashSet<>(ids)).forEach(u -> map.put(u.getId(), u));
        return map;
    }
}
