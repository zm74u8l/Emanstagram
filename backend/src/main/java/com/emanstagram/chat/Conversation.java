package com.emanstagram.chat;

import com.emanstagram.common.Times;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A DM between two people or a named group chat. */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private ConversationKind kind = ConversationKind.DIRECT;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "avatar_key", length = 500)
    private String avatarKey;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected Conversation() {
    }

    public Conversation(ConversationKind kind, String title, UUID createdBy) {
        this.kind = kind;
        this.title = title;
        this.createdBy = createdBy;
    }

    public boolean isGroup() {
        return kind == ConversationKind.GROUP;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return id != null && id.equals(((Conversation) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public ConversationKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
