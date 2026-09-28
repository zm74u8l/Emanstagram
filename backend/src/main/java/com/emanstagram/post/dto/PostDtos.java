package com.emanstagram.post.dto;

import com.emanstagram.post.PostKind;
import com.emanstagram.post.PostVisibility;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payloads for posts, likes and saves. */
public final class PostDtos {

    private PostDtos() {
    }

    public record MediaItem(
            UUID id,
            String url,
            String mimeType,
            Integer width,
            Integer height,
            Integer durationMs,
            String blurhash
    ) {
    }

    public record PostResponse(
            UUID id,
            UserSummary author,
            String caption,
            PostKind kind,
            PostVisibility visibility,
            String location,
            List<MediaItem> media,
            int likeCount,
            int commentCount,
            boolean likedByMe,
            boolean savedByMe,
            Instant createdAt,
            Instant editedAt
    ) {
    }

    /**
     * Per-file metadata measured in the browser, sent alongside the upload as
     * a JSON array in the same order as the files.
     */
    public record MediaMeta(Integer width, Integer height, Integer durationMs, String blurhash) {
    }

    public record UpdatePostRequest(
            @Size(max = 2200, message = "Captions are limited to 2200 characters")
            String caption,

            @Size(max = 160, message = "Location is limited to 160 characters")
            String location,

            PostVisibility visibility
    ) {
    }

    public record LikeState(boolean liked, int likeCount) {
    }

    public record SaveState(boolean saved) {
    }

    /** One tile of the signed-out login mosaic. */
    public record MosaicTile(String url, String blurhash, Integer width, Integer height) {
    }
}
