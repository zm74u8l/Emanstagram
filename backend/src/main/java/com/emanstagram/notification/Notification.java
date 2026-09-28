package com.emanstagram.notification;

import com.emanstagram.common.Times;
import com.emanstagram.user.User;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One entry in a user's activity feed. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private NotificationKind kind;

    @Column(name = "post_id", updatable = false)
    private UUID postId;

    @Column(name = "comment_id", updatable = false)
    private UUID commentId;

    @Column(name = "message", length = 300)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected Notification() {
    }

    public Notification(UUID recipientId, User actor, NotificationKind kind,
                        UUID postId, UUID commentId, String message) {
        this.recipientId = recipientId;
        this.actor = actor;
        this.kind = kind;
        this.postId = postId;
        this.commentId = commentId;
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return id != null && id.equals(((Notification) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public User getActor() {
        return actor;
    }

    public NotificationKind getKind() {
        return kind;
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getCommentId() {
        return commentId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
