package com.emanstagram.common;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset-pagination cursor: the {@code (createdAt, id)} of the last
 * row on the previous page.
 *
 * <p>Keyset pagination stays fast at any depth and never shows a row twice
 * when new content arrives between page loads, both of which OFFSET gets
 * wrong for a feed.
 */
public record Cursor(Instant createdAt, UUID id) {

    /** Sorts after everything, so a descending query's first page needs no special case. */
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");
    /** Sorts before everything, for ascending queries. */
    private static final Instant FAR_PAST = Instant.parse("1970-01-01T00:00:00Z");
    private static final UUID MAX_ID = new UUID(-1L, -1L);
    private static final UUID MIN_ID = new UUID(0L, 0L);

    public static Cursor startDescending() {
        return new Cursor(FAR_FUTURE, MAX_ID);
    }

    public static Cursor startAscending() {
        return new Cursor(FAR_PAST, MIN_ID);
    }

    public String encode() {
        String raw = createdAt.getEpochSecond() + ":" + createdAt.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a client cursor; a null/blank value means "first page". */
    public static Cursor decodeDescending(String value) {
        return value == null || value.isBlank() ? startDescending() : decode(value);
    }

    public static Cursor decodeAscending(String value) {
        return value == null || value.isBlank() ? startAscending() : decode(value);
    }

    private static Cursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 3);
            Instant at = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            return new Cursor(at, UUID.fromString(parts[2]));
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("INVALID_CURSOR", "That page link is no longer valid.");
        }
    }
}
