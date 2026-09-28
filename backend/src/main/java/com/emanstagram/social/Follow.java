package com.emanstagram.social;

import com.emanstagram.common.Times;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A directed follow edge: follower -> followee. */
@Entity
@Table(name = "follows")
@IdClass(Follow.Key.class)
public class Follow {

    @Id
    @Column(name = "follower_id", nullable = false, updatable = false)
    private UUID followerId;

    @Id
    @Column(name = "followee_id", nullable = false, updatable = false)
    private UUID followeeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected Follow() {
    }

    public Follow(UUID followerId, UUID followeeId) {
        this.followerId = followerId;
        this.followeeId = followeeId;
    }

    public UUID getFollowerId() {
        return followerId;
    }

    public UUID getFolloweeId() {
        return followeeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {
        private UUID followerId;
        private UUID followeeId;

        protected Key() {
        }

        public Key(UUID followerId, UUID followeeId) {
            this.followerId = followerId;
            this.followeeId = followeeId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(followerId, k.followerId) && Objects.equals(followeeId, k.followeeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(followerId, followeeId);
        }
    }
}
