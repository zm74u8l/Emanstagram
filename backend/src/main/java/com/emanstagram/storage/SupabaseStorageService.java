package com.emanstagram.storage;

import com.emanstagram.common.ApiException;
import com.emanstagram.config.EmanstagramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to Supabase Storage so media bytes never touch the database.
 *
 * <p>The service role key is used server-side only. It must never be shipped
 * to the browser: browsers upload through this API, which forwards the file,
 * keeping the privileged key on this side of the connection.
 *
 * <p>Every storage key starts with its kind ({@code posts/…}, {@code avatars/…},
 * {@code stories/…}, {@code messages/…}), and the bucket is derived from that
 * prefix. A key saved in the database therefore always resolves to the right
 * bucket without a second column.
 */
@Service
public class SupabaseStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    public static final String POSTS = "posts";
    public static final String AVATARS = "avatars";
    public static final String STORIES = "stories";
    public static final String MESSAGES = "messages";

    /** Re-sign a cached URL once less than this much of its lifetime is left. */
    private static final Duration RESIGN_MARGIN = Duration.ofMinutes(10);

    private final EmanstagramProperties properties;
    private final RestClient restClient;
    private final boolean configured;
    private final String baseUrl;

    private final Map<String, SignedUrl> signedCache = new ConcurrentHashMap<>();

    private record SignedUrl(String url, Instant expiresAt) {
    }

    public SupabaseStorageService(EmanstagramProperties properties) {
        this.properties = properties;
        this.configured = isConfigured(properties);
        this.baseUrl = configured ? properties.storage().url().replaceAll("/+$", "") : "";

        var factory = new SimpleClientHttpRequestFactory();
        // 20MB uploads need more than the 5s default on a cold connection.
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));

        String key = properties.storage() == null ? "" : properties.storage().serviceRoleKey();
        this.restClient = RestClient.builder()
                // Both headers, as scripts/create-buckets.ps1 sends them. The
                // newer sb_secret_ keys are recognised through `apikey`; the
                // legacy service_role JWT through Authorization.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .defaultHeader("apikey", key == null ? "" : key)
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
                baseUrl, bucketForKind(kindOf(storageKey)), encodePath(storageKey));
    }

    /**
     * Time-limited signed URL, used for private buckets (chat attachments).
     * Falls back to the public URL for public buckets.
     */
    public String signedUrl(String storageKey, Duration ttl) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        return signedUrls(List.of(storageKey), ttl).get(storageKey);
    }

    /**
     * Signs many keys in one round trip.
     *
     * <p>A page of chat history can hold dozens of attachments, and signing
     * them one request at a time would make scrolling crawl. Results are
     * cached until shortly before they expire, so re-reading a thread costs
     * no storage calls at all.
     */
    public Map<String, String> signedUrls(Collection<String> storageKeys, Duration ttl) {
        Map<String, String> result = new HashMap<>();
        if (!configured || storageKeys == null || storageKeys.isEmpty()) {
            return result;
        }

        Instant now = Instant.now();
        Map<String, List<String>> toSignByBucket = new HashMap<>();
        for (String key : new LinkedHashSet<>(storageKeys)) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String bucket = bucketForKind(kindOf(key));
            if (isPublicBucket(bucket)) {
                result.put(key, publicUrl(key));
                continue;
            }
            SignedUrl cached = signedCache.get(key);
            if (cached != null && cached.expiresAt().minus(RESIGN_MARGIN).isAfter(now)) {
                result.put(key, cached.url());
            } else {
                toSignByBucket.computeIfAbsent(bucket, b -> new ArrayList<>()).add(key);
            }
        }

        toSignByBucket.forEach((bucket, keys) -> {
            try {
                JsonNode response = restClient.post()
                        .uri(URI.create("%s/storage/v1/object/sign/%s".formatted(baseUrl, bucket)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("expiresIn", ttl.toSeconds(), "paths", keys))
                        .retrieve()
                        .body(JsonNode.class);

                Instant expiry = now.plus(ttl);
                if (response != null && response.isArray()) {
                    for (JsonNode item : response) {
                        String path = item.path("path").asText(null);
                        String signed = item.path("signedURL").asText(null);
                        if (path != null && signed != null && !signed.isBlank()) {
                            // signedURL is relative to /storage/v1.
                            String url = baseUrl + "/storage/v1" + signed;
                            signedCache.put(path, new SignedUrl(url, expiry));
                            result.put(path, url);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to sign {} key(s) in {}: {}", keys.size(), bucket, ex.getMessage());
            }
        });
        return result;
    }

    /**
     * Uploads bytes and returns the storage key to persist.
     *
     * <p>The key is {@code <kind>/<owner>/<uuid>.<ext>}. Two uploads can never
     * collide, every object is attributable to its owner, and the kind prefix
     * pins the bucket.
     *
     * @param kind  one of {@link #POSTS}, {@link #AVATARS}, {@link #STORIES}, {@link #MESSAGES}
     * @param owner the user or conversation id the object belongs to
     */
    public String upload(String kind, UUID owner, byte[] data, String contentType, String extension) {
        requireConfigured();

        String key = "%s/%s/%s.%s".formatted(
                kind,
                owner,
                UUID.randomUUID(),
                (extension == null || extension.isBlank()) ? "bin" : extension);
        String bucket = bucketForKind(kind);

        try {
            restClient.post()
                    // A prebuilt URI bypasses template expansion, which would
                    // otherwise encode the slashes in the key as %2F.
                    .uri(objectUri(bucket, key))
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("cache-control", "max-age=31536000, immutable")
                    .body(data)
                    .retrieve()
                    .toBodilessEntity();
            return key;
        } catch (Exception ex) {
            log.error("Storage upload failed for {}/{}", bucket, key, ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "UPLOAD_FAILED", "The upload could not be completed. Please try again.");
        }
    }

    /** Best-effort delete; a missing object is not treated as an error. */
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || !configured) {
            return;
        }
        deleteAll(List.of(storageKey));
    }

    /** Best-effort bulk delete, one request per bucket. */
    public void deleteAll(Collection<String> storageKeys) {
        if (!configured || storageKeys == null || storageKeys.isEmpty()) {
            return;
        }
        Map<String, List<String>> byBucket = new HashMap<>();
        for (String key : storageKeys) {
            if (key != null && !key.isBlank()) {
                byBucket.computeIfAbsent(bucketForKind(kindOf(key)), b -> new ArrayList<>()).add(key);
                signedCache.remove(key);
            }
        }
        byBucket.forEach((bucket, keys) -> {
            try {
                restClient.method(org.springframework.http.HttpMethod.DELETE)
                        .uri(URI.create("%s/storage/v1/object/%s".formatted(baseUrl, bucket)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("prefixes", keys))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ex) {
                log.warn("Failed to delete {} object(s) from {}: {}", keys.size(), bucket, ex.getMessage());
            }
        });
    }

    public void requireConfigured() {
        if (!configured) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "STORAGE_NOT_CONFIGURED",
                    "Media storage is not configured on this server yet.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    // ---------------- helpers ----------------

    private URI objectUri(String bucket, String key) {
        return URI.create("%s/storage/v1/object/%s/%s".formatted(baseUrl, bucket, encodePath(key)));
    }

    /** Maps the kind prefix of a key to the configured bucket name. */
    private String bucketForKind(String kind) {
        var buckets = properties.storage().buckets();
        if (buckets == null) {
            return kind;
        }
        return switch (kind) {
            case AVATARS -> buckets.avatars();
            case STORIES -> buckets.stories();
            case MESSAGES -> buckets.messages();
            default -> buckets.posts();
        };
    }

    private static String kindOf(String storageKey) {
        int slash = storageKey.indexOf('/');
        return slash > 0 ? storageKey.substring(0, slash) : POSTS;
    }

    /** Content buckets are public and CDN-cached; only `messages` is private. */
    private boolean isPublicBucket(String bucket) {
        var buckets = properties.storage().buckets();
        String privateBucket = buckets == null ? MESSAGES : buckets.messages();
        return !bucket.equals(privateBucket);
    }

    /** URL-encodes each path segment while keeping the slashes intact. */
    private static String encodePath(String path) {
        return Arrays.stream(path.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((a, b) -> a + "/" + b)
                .orElse(path);
    }
}
