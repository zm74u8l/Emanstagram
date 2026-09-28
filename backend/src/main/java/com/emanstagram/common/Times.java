package com.emanstagram.common;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Clock helpers shared by every entity. */
public final class Times {

    private Times() {
    }

    /**
     * The current instant, truncated to microseconds.
     *
     * <p>Postgres stores microseconds while the JVM can produce more precision.
     * Truncating before persisting means the value in memory equals the stored
     * one, so a keyset cursor built from a freshly saved row never skips it.
     */
    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
