package com.emanstagram.comment;

import com.emanstagram.common.Times;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A user's like on a comment. */
@Entity
@Table(name = "comment_likes")
@IdClass(CommentLike.Key.class)
public class CommentLike {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "comment_id", nullable = false, updatable = false)
    private UUID commentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected CommentLike() {
    }

    public CommentLike(UUID userId, UUID commentId) {
        this.userId = userId;
        this.commentId = commentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCommentId() {
        return commentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {
        private UUID userId;
        private UUID commentId;

        protected Key() {
        }

        public Key(UUID userId, UUID commentId) {
            this.userId = userId;
            this.commentId = commentId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(commentId, k.commentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, commentId);
        }
    }
}
