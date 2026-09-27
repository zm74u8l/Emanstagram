package com.emanstagram.storage;

import com.emanstagram.common.ApiException;
import com.emanstagram.config.EmanstagramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * Talks to Supabase Storage so media bytes never touch the database.
 *
 * <p>The service role key is used server-side only. It must never be shipped
 * to the browser: browsers upload through this API, which forwards the file,
 * keeping the privileged key on this side of the connection.
 */
@Service
public class SupabaseStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final EmanstagramProperties properties;
    private final RestClient restClient;
    private final boolean configured;

    public SupabaseStorageService(EmanstagramProperties properties) {
        this.properties = properties;
        this.configured = isConfigured(properties);

        var factory = new SimpleClientHttpRequestFactory();
        // 20MB uploads need more than the 5s default on a cold connection.
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));

        this.restClient = RestClient.builder()
                .baseUrl(properties.storage() == null ? "" : properties.storage().url())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + (properties.storage() == null
                                ? "" : properties.storage().serviceRoleKey()))
                .requestFactory(factory)
                .build();

        if (!configured) {
            log.warn("Supabase Storage is not configured. Media endpoints return a clear "
                    + "error until SUPABASE_URL and SUPABASE_SECRET_KEY are set.");
        }
    }

    private static boolean isConfigured(EmanstagramProperties properties) {
        var storage = properties.storage();
        return storage != null
                && storage.url() != null && !storage.url().isBlank()
                && storage.serviceRoleKey() != null && !storage.serviceRoleKey().isBlank();
    }

    /**
     * Public CDN URL for a storage key. Returns null when no key is set or
     * Supabase is not configured, so callers can fall back to an identicon.
     */
    public String publicUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || !configured) {
            return null;
        }
        return "%s/storage/v1/object/public/%s/%s".formatted(
                properties.storage().url().replaceAll("/+$", ""),
                bucketForKind(kindOf(storageKey)),
                encodePath(storageKey));
    }

    /**
     * Time-limited signed URL, used for private buckets (chat attachments).
     * Falls back to the public URL for public buckets.
     */
    public String signedUrl(String storageKey, Duration ttl) {
        if (storageKey == null || storageKey.isBlank() || !configured) {
            return null;
        }
        String bucket = bucketForKind(kindOf(storageKey));
        if (isPublicBucket(bucket)) {
            return publicUrl(storageKey);
        }
        try {
            String encoded = URLEncoder.encode(storageKey, StandardCharsets.UTF_8);
            JsonNode response = restClient.get()
                    .uri("/storage/v1/object/sign/{bucket}/{path}?expires={ttl}",
                            bucket, encoded, ttl.toSeconds())
                    .retrieve()
                    .body(JsonNode.class);

            String token = response != null ? response.path("signedURL").asText(null) : null;
            if (token == null || token.isBlank()) {
                return publicUrl(storageKey);
            }
            return properties.storage().url() + token;
        } catch (Exception ex) {
            log.warn("Failed to sign {}: {}", storageKey, ex.getMessage());
            return publicUrl(storageKey);
        }
    }

    /**
     * Uploads bytes to a bucket and returns the storage key to persist.
     *
     * <p>The key is namespaced {@code <folder>/<uuid>.<ext>} so two uploads can
     * never collide and every object is trivially attributable to a user.
     */
    public String upload(String folder, String bucket, byte[] data,
                         String contentType, String extension) {
        requireConfigured();

        String key = "%s/%s.%s".formatted(
                stripSlashes(folder == null ? "misc" : folder),
                UUID.randomUUID(),
                (extension == null || extension.isBlank()) ? "bin" : extension);

        try {
            restClient.post()
                    .uri("/storage/v1/object/{bucket}/{path}", bucket, encodePath(key))
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data)
                    .retrieve()
                    .toBodilessEntity();
            return key;
        } catch (Exception ex) {
            log.error("Storage upload failed for {}/{}", bucket, key, ex);
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "UPLOAD_FAILED", "The upload could not be completed. Please try again.");
        }
    }

    /** Best-effort delete; a missing object is not treated as an error. */
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || !configured) {
            return;
        }
        try {
            restClient.delete()
                    .uri("/storage/v1/object/{bucket}/{path}",
                            bucketForKind(kindOf(storageKey)), encodePath(storageKey))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Failed to delete {}: {}", storageKey, ex.getMessage());
        }
    }

    public void requireConfigured() {
        if (!configured) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "STORAGE_NOT_CONFIGURED",
                    "Media storage is not configured on this server yet.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    // ---------------- helpers ----------------

    /**
     * Infers the bucket from the folder prefix in the key, so a key persisted
     * in the database always resolves to its bucket without a second column.
     */
    private String bucketForKind(String folderPrefix) {
        var buckets = properties.storage().buckets();
        if (buckets == null) {
            return "posts";
        }
        return switch (folderPrefix) {
            case "avatars" -> buckets.avatars();
            case "stories" -> buckets.stories();
            case "messages" -> buckets.messages();
            default -> buckets.posts();
        };
    }

    private static String kindOf(String storageKey) {
        int slash = storageKey.indexOf('/');
        return slash > 0 ? storageKey.substring(0, slash) : "posts";
    }

    /** Content buckets are public and CDN-cached; only `messages` is private. */
    private boolean isPublicBucket(String bucket) {
        var buckets = properties.storage().buckets();
        return buckets == null || !bucket.equals(buckets.messages());
    }

    /** URL-encodes each path segment while keeping the slashes intact. */
    private static String encodePath(String path) {
        return Arrays.stream(path.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((a, b) -> a + "/" + b)
                .orElse(path);
    }

    /** String.trim only strips whitespace, so leading/trailing slashes need help. */
    private static String stripSlashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        String stripped = value.substring(start, end);
        return stripped.isEmpty() ? "misc" : stripped;
    }
}
