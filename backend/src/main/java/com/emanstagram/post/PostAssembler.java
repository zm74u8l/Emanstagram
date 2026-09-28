package com.emanstagram.post;

import com.emanstagram.post.dto.PostDtos.MediaItem;
import com.emanstagram.post.dto.PostDtos.PostResponse;
import com.emanstagram.storage.SupabaseStorageService;
import com.emanstagram.user.UserViews;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Builds {@link PostResponse}s for a whole page at once.
 *
 * <p>A naive mapper issues media, like and save lookups per post, which is
 * 3 × 12 queries for one feed page. This does one query of each kind for the
 * page, whatever its size.
 */
@Component
public class PostAssembler {

    private final PostMediaRepository media;
    private final PostLikeRepository likes;
    private final SavedPostRepository saves;
    private final UserViews userViews;
    private final SupabaseStorageService storage;

    public PostAssembler(PostMediaRepository media, PostLikeRepository likes,
                         SavedPostRepository saves, UserViews userViews,
                         SupabaseStorageService storage) {
        this.media = media;
        this.likes = likes;
        this.saves = saves;
        this.userViews = userViews;
        this.storage = storage;
    }

    /** Posts must have their author loaded (the repositories use an entity graph for that). */
    public List<PostResponse> assemble(List<Post> posts, UUID viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        posts.forEach(p -> ids.add(p.getId()));

        Map<UUID, List<MediaItem>> mediaByPost = new HashMap<>();
        for (PostMedia m : media.findForPosts(ids)) {
            mediaByPost.computeIfAbsent(m.getPost().getId(), k -> new ArrayList<>()).add(toItem(m));
        }
        Set<UUID> liked = likes.likedAmong(viewerId, ids);
        Set<UUID> saved = saves.savedAmong(viewerId, ids);

        List<PostResponse> result = new ArrayList<>(posts.size());
        for (Post p : posts) {
            result.add(new PostResponse(
                    p.getId(),
                    userViews.summary(p.getAuthor()),
                    p.getCaption(),
                    p.getKind(),
                    p.getVisibility(),
                    p.getLocation(),
                    mediaByPost.getOrDefault(p.getId(), List.of()),
                    p.getLikeCount(),
                    p.getCommentCount(),
                    liked.contains(p.getId()),
                    saved.contains(p.getId()),
                    p.getCreatedAt(),
                    p.getEditedAt()));
        }
        return result;
    }

    public PostResponse assemble(Post post, UUID viewerId) {
        return assemble(List.of(post), viewerId).get(0);
    }

    public MediaItem toItem(PostMedia m) {
        return new MediaItem(m.getId(), storage.publicUrl(m.getStorageKey()), m.getMimeType(),
                m.getWidth(), m.getHeight(), m.getDurationMs(), m.getBlurhash());
    }
}
