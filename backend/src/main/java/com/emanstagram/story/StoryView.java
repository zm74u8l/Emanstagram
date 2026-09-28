package com.emanstagram.story;

import com.emanstagram.common.Times;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A user having seen a story, which drives the unseen ring and the viewers list. */
@Entity
@Table(name = "story_views")
@IdClass(StoryView.Key.class)
public class StoryView {

    @Id
    @Column(name = "story_id", nullable = false, updatable = false)
    private UUID storyId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    private Instant viewedAt = Times.now();

    protected StoryView() {
    }

    public StoryView(UUID storyId, UUID userId) {
        this.storyId = storyId;
        this.userId = userId;
    }

    public UUID getStoryId() {
        return storyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getViewedAt() {
        return viewedAt;
    }

    /** Composite primary key. */
    public static class Key implements Serializable {
        private UUID storyId;
        private UUID userId;

        protected Key() {
        }

        public Key(UUID storyId, UUID userId) {
            this.storyId = storyId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(storyId, k.storyId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storyId, userId);
        }
    }
}
