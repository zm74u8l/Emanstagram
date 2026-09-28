package com.emanstagram.chat;

import com.emanstagram.common.Times;
import com.emanstagram.user.User;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A chat message. Deletes are soft ({@code deletedAt}) so replies that quote
 * a deleted message still render "Message deleted" instead of vanishing.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    /** Null for SYSTEM messages and when the sender's account was deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", updatable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private MessageKind kind = MessageKind.TEXT;

    @Column(name = "body", length = 4000)
    private String body;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "reply_to_id", updatable = false)
    private UUID replyToId;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected Message() {
    }

    public Message(UUID conversationId, User sender, MessageKind kind, String body,
                   String storageKey, UUID replyToId) {
        this.conversationId = conversationId;
        this.sender = sender;
        this.kind = kind;
        this.body = body;
        this.storageKey = storageKey;
        this.replyToId = replyToId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getSenderId() {
        return sender == null ? null : sender.getId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return id != null && id.equals(((Message) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public User getSender() {
        return sender;
    }

    public MessageKind getKind() {
        return kind;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public UUID getReplyToId() {
        return replyToId;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Instant editedAt) {
        this.editedAt = editedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
