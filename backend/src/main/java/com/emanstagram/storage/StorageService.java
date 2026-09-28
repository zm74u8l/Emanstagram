package com.emanstagram.storage;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Where media bytes live. The database only ever stores the object key.
 *
 * <p>Every key starts with its kind ({@code posts/…}, {@code avatars/…},
 * {@code stories/…}, {@code messages/…}), which decides whether it is public
 * or private. {@code messages} is private and only reachable through
 * short-lived signed URLs; everything else is publicly cacheable.
 *
 * <p>Two implementations, chosen by {@code emanstagram.storage.provider}:
 * {@link SupabaseStorageService} ({@code supabase}, the default) and
 * {@link S3StorageService} ({@code s3}: Cloudflare R2, MinIO, Backblaze,
 * AWS). Keys are identical in both, so switching provider only needs the
 * objects copied across, not a database change.
 */
public interface StorageService {

    String POSTS = "posts";
    String AVATARS = "avatars";
    String STORIES = "stories";
    String MESSAGES = "messages";

    /** Public URL for a key, or null when there is no key or storage is off (callers fall back to an identicon). */
    String publicUrl(String storageKey);

    /** A time-limited URL for a private key; public keys get their public URL. */
    default String signedUrl(String storageKey, Duration ttl) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        return signedUrls(java.util.List.of(storageKey), ttl).get(storageKey);
    }

    /** Signs many keys at once, keyed by storage key. */
    Map<String, String> signedUrls(Collection<String> storageKeys, Duration ttl);

    /**
     * Stores the bytes and returns the key to persist: {@code <kind>/<owner>/<uuid>.<ext>}.
     *
     * @param kind  one of {@link #POSTS}, {@link #AVATARS}, {@link #STORIES}, {@link #MESSAGES}
     * @param owner the user or conversation the object belongs to
     */
    String upload(String kind, UUID owner, byte[] data, String contentType, String extension);

    /** Best-effort delete; a missing object is not an error. */
    default void delete(String storageKey) {
        if (storageKey != null && !storageKey.isBlank()) {
            deleteAll(java.util.List.of(storageKey));
        }
    }

    /** Best-effort bulk delete. */
    void deleteAll(Collection<String> storageKeys);

    boolean isConfigured();

    /** Throws a clear 503 when storage isn't set up, instead of failing mid-upload. */
    void requireConfigured();

    /** The kind prefix of a key: "posts" for "posts/abc/def.webp". */
    static String kindOf(String storageKey) {
        int slash = storageKey.indexOf('/');
        return slash > 0 ? storageKey.substring(0, slash) : POSTS;
    }

    /** New keys are namespaced so two uploads can never collide. */
    static String newKey(String kind, UUID owner, String extension) {
        return "%s/%s/%s.%s".formatted(kind, owner, UUID.randomUUID(),
                (extension == null || extension.isBlank()) ? "bin" : extension);
    }
}
