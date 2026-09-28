package com.emanstagram.social;

import com.emanstagram.common.ApiException;
import com.emanstagram.post.Post;
import com.emanstagram.post.PostVisibility;
import com.emanstagram.user.Role;
import com.emanstagram.user.User;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The single place that decides who may see what.
 *
 * <ul>
 *   <li>PUBLIC posts: everyone</li>
 *   <li>FOLLOWERS posts: the author's followers and the author</li>
 *   <li>PRIVATE posts: the author only</li>
 *   <li>A block in either direction hides everything, whatever the visibility</li>
 *   <li>Moderators can see everything, so they can review reports</li>
 * </ul>
 */
@Component
public class AccessPolicy {

    /**
     * Stands in for an empty id set. {@code NOT IN ()} is not valid SQL on
     * every database, so queries always receive at least this one id, which
     * can never match a real row.
     */
    private static final UUID NOBODY = new UUID(0L, 0L);

    private final FollowRepository follows;
    private final BlockRepository blocks;

    public AccessPolicy(FollowRepository follows, BlockRepository blocks) {
        this.follows = follows;
        this.blocks = blocks;
    }

    public boolean canView(Post post, User viewer) {
        UUID author = post.getAuthorId();
        if (author.equals(viewer.getId()) || isModerator(viewer)) {
            return true;
        }
        if (blocks.existsEitherWay(author, viewer.getId())) {
            return false;
        }
        return switch (post.getVisibility()) {
            case PUBLIC -> true;
            case FOLLOWERS -> follows.existsByFollowerIdAndFolloweeId(viewer.getId(), author);
            case PRIVATE -> false;
        };
    }

    /**
     * Returns the post or a 404. Deliberately not 403: confirming that a
     * private post exists leaks information.
     */
    public Post requireVisible(Post post, User viewer) {
        if (!canView(post, viewer)) {
            throw postNotFound();
        }
        return post;
    }

    /** Which visibilities of {@code authorId}'s posts the viewer may list. */
    public Set<PostVisibility> visibleOn(UUID authorId, UUID viewerId) {
        if (authorId.equals(viewerId)) {
            return EnumSet.allOf(PostVisibility.class);
        }
        if (follows.existsByFollowerIdAndFolloweeId(viewerId, authorId)) {
            return EnumSet.of(PostVisibility.PUBLIC, PostVisibility.FOLLOWERS);
        }
        return EnumSet.of(PostVisibility.PUBLIC);
    }

    /** Everyone hidden from the viewer by a block, never empty (see {@link #NOBODY}). */
    public Set<UUID> hiddenFrom(UUID viewerId) {
        Set<UUID> hidden = new HashSet<>(blocks.hiddenFrom(viewerId));
        hidden.add(NOBODY);
        return hidden;
    }

    public boolean blockedEitherWay(UUID a, UUID b) {
        return blocks.existsEitherWay(a, b);
    }

    public static boolean isModerator(User user) {
        return user.getRole().atLeast(Role.MODERATOR);
    }

    public static ApiException postNotFound() {
        return ApiException.notFound("POST_NOT_FOUND", "This post isn't available.");
    }
}
