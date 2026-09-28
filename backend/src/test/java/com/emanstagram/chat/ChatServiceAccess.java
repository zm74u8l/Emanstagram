package com.emanstagram.chat;

import java.util.UUID;

/** Exposes package-private chat helpers to tests in other packages. */
public final class ChatServiceAccess {

    private ChatServiceAccess() {
    }

    public static UUID[] orderedPair(UUID a, UUID b) {
        return ChatService.orderedPair(a, b);
    }
}
