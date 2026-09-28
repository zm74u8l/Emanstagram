package com.emanstagram.story;

import com.emanstagram.common.Times;
import com.emanstagram.user.User;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An ephemeral photo or video that disappears after 24 hours. */
@Entity
@Table(name = "stories")
public class Story {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "caption", length = 300)
    private String caption;

    @Column(name = "background_hex", length = 9)
    private String backgroundHex;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected Story() {
    }

    public Story(User author, String storageKey, String mimeType, String caption,
                 String backgroundHex, Instant expiresAt) {
        this.author = author;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.caption = caption;
        this.backgroundHex = backgroundHex;
        this.expiresAt = expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return id != null && id.equals(((Story) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public UUID getAuthorId() {
        return author.getId();
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getCaption() {
        return caption;
    }

    public String getBackgroundHex() {
        return backgroundHex;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
