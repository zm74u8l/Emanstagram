package com.emanstagram.social;

import com.emanstagram.common.Times;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** blocker has blocked blocked. Hides content and prevents contact in both directions. */
@Entity
@Table(name = "blocks")
@IdClass(Block.Key.class)
public class Block {

    @Id
    @Column(name = "blocker_id", nullable = false, updatable = false)
    private UUID blockerId;

    @Id
    @Column(name = "blocked_id", nullable = false, updatable = false)
    private UUID blockedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected Block() {
    }

    public Block(UUID blockerId, UUID blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    public UUID getBlockerId() {
        return blockerId;
    }

    public UUID getBlockedId() {
        return blockedId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {
        private UUID blockerId;
        private UUID blockedId;

        protected Key() {
        }

        public Key(UUID blockerId, UUID blockedId) {
            this.blockerId = blockerId;
            this.blockedId = blockedId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(blockerId, k.blockerId) && Objects.equals(blockedId, k.blockedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockerId, blockedId);
        }
    }
}
