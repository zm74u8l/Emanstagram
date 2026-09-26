package com.emanstagram.user;

/** Platform role. Ordered least to most privileged. */
public enum Role {
    USER,
    MODERATOR,
    ADMIN;

    /** True when this role satisfies a required minimum privilege. */
    public boolean atLeast(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}
