package com.emanstagram.post;

import com.emanstagram.common.Times;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A user's like on a post. The composite key makes a double-like impossible. */
@Entity
@Table(name = "post_likes")
@IdClass(PostLike.Key.class)
public class PostLike {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected PostLike() {
    }

    public PostLike(UUID userId, UUID postId) {
        this.userId = userId;
        this.postId = postId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPostId() {
        return postId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {
        private UUID userId;
        private UUID postId;

        protected Key() {
        }

        public Key(UUID userId, UUID postId) {
            this.userId = userId;
            this.postId = postId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(postId, k.postId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, postId);
        }
    }
}
