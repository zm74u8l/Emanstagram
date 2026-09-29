package com.emanstagram.common;

/**
 * Makes user input safe to put inside a LIKE pattern. Without this, a search
 * for "_" or "%" is read as a wildcard and matches everything.
 *
 * <p>Queries using it must say {@code ESCAPE '!'}. The escape is spelled out
 * because the default differs (backslash on Postgres, none on H2 through
 * Hibernate), and it's '!' rather than backslash because HQL string literals
 * treat backslash as an escape of their own.
 */
public final class SqlLike {

    private SqlLike() {
    }

    public static String escape(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
