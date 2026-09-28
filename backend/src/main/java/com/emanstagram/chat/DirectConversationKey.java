package com.emanstagram.chat;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps an unordered pair of users to their single DM.
 *
 * <p>The pair is stored ordered ({@code userLow < userHigh}) so (a, b) and
 * (b, a) hit the same primary key, which makes a duplicate DM impossible
 * even when both people press "Message" at the same moment.
 */
@Entity
@Table(name = "direct_conversation_keys")
@IdClass(DirectConversationKey.Key.class)
public class DirectConversationKey {

    @Id
    @Column(name = "user_low", nullable = false, updatable = false)
    private UUID userLow;

    @Id
    @Column(name = "user_high", nullable = false, updatable = false)
    private UUID userHigh;

    @Column(name = "conversation_id", nullable = false, unique = true, updatable = false)
    private UUID conversationId;

    protected DirectConversationKey() {
    }

    public DirectConversationKey(UUID userLow, UUID userHigh, UUID conversationId) {
        this.userLow = userLow;
        this.userHigh = userHigh;
        this.conversationId = conversationId;
    }

    public UUID getUserLow() {
        return userLow;
    }

    public UUID getUserHigh() {
        return userHigh;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {
        private UUID userLow;
        private UUID userHigh;

        protected Key() {
        }

        public Key(UUID userLow, UUID userHigh) {
            this.userLow = userLow;
            this.userHigh = userHigh;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userLow, k.userLow)
                    && Objects.equals(userHigh, k.userHigh);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userLow, userHigh);
        }
    }
}
