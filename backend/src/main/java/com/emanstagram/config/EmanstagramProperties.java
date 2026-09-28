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

    /**
     * Media storage. {@code provider} picks the backend: {@code supabase}
     * (Supabase Storage, the original setup) or {@code s3} (any
     * S3-compatible store, e.g. Cloudflare R2, which has no egress fees).
     */
    public record Storage(String provider, String url, String serviceRoleKey, Buckets buckets, S3 s3) {
        public record Buckets(String posts, String avatars, String stories, String messages) {
        }

        /**
         * @param publicBucket  holds posts/, avatars/ and stories/, readable by anyone
         * @param privateBucket holds messages/, only reachable through signed URLs
         * @param publicBaseUrl where the public bucket is served from: an R2
         *                      custom domain or r2.dev URL, a CDN, or a MinIO URL
         * @param pathStyle     true for MinIO and most self-hosted stores;
         *                      R2 accepts either
         */
        public record S3(String endpoint, String region, String accessKeyId, String secretAccessKey,
                         String publicBucket, String privateBucket, String publicBaseUrl,
                         boolean pathStyle) {
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
