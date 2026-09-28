package com.emanstagram.chat;

import com.emanstagram.common.Times;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Membership of a conversation.
 *
 * <p>{@code lastReadAt} drives both unread counts and the "Seen" marker, so
 * reading a thread is one row update rather than one receipt per message.
 */
@Entity
@Table(name = "conversation_members")
@IdClass(ConversationMember.Key.class)
public class ConversationMember {

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";

    @Id
    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "role", nullable = false, length = 20)
    private String role = MEMBER;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Times.now();

    protected ConversationMember() {
    }

    public ConversationMember(UUID conversationId, UUID userId, String role) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
    }

    public boolean canManage() {
        return OWNER.equals(role) || ADMIN.equals(role);
    }

    // --- accessors ---

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    public void setLastReadAt(Instant lastReadAt) {
        this.lastReadAt = lastReadAt;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {
        private UUID conversationId;
        private UUID userId;

        protected Key() {
        }

        public Key(UUID conversationId, UUID userId) {
            this.conversationId = conversationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(conversationId, k.conversationId)
                    && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversationId, userId);
        }
    }
}
