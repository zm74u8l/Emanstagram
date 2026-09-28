package com.emanstagram.common;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a keyset-paginated list. {@code nextCursor} is null on the
 * last page.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public static final int DEFAULT_LIMIT = 12;
    public static final int MAX_LIMIT = 50;

    /** Clamps a client-supplied page size into a safe range. */
    public static int clamp(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * Builds a page from rows fetched with {@code limit + 1}: the extra row
     * only proves another page exists and is not returned.
     */
    public static <R, T> CursorPage<T> of(List<R> rows, int limit,
                                          Function<R, Cursor> cursorOf,
                                          Function<List<R>, List<T>> mapper) {
        boolean more = rows.size() > limit;
        List<R> page = more ? rows.subList(0, limit) : rows;
        String next = more ? cursorOf.apply(page.get(page.size() - 1)).encode() : null;
        return new CursorPage<>(mapper.apply(page), next);
    }
}
