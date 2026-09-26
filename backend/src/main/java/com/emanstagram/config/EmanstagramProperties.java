package com.emanstagram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Strongly-typed binding for all {@code emanstagram.*} configuration.
 * Values come from application.yml / environment variables.
 */
@ConfigurationProperties(prefix = "emanstagram")
public record EmanstagramProperties(
        Jwt jwt,
        Cors cors,
        Storage storage,
        Media media
) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl, String issuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Storage(String url, String serviceRoleKey, Buckets buckets) {
        public record Buckets(String posts, String avatars, String stories, String messages) {
        }
    }

    /**
     * Upload caps. These are the single source of truth; the API also
     * mirrors them to the frontend via /api/config so the client can
     * pre-validate before spending bandwidth.
     */
    public record Media(
            long maxImageBytes,
            long maxVideoBytes,
            long maxAvatarBytes,
            long maxStoryVideoBytes,
            int maxCarouselItems,
            int maxCaptionLength,
            int maxCommentLength,
            List<String> allowedImageTypes,
            List<String> allowedVideoTypes
    ) {
        public boolean imageTypeAllowed(String mime) {
            return allowedImageTypes != null && allowedImageTypes.contains(mime);
        }

        public boolean videoTypeAllowed(String mime) {
            return allowedVideoTypes != null && allowedVideoTypes.contains(mime);
        }
    }
}
