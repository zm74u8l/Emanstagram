package com.emanstagram.post;

import com.emanstagram.common.Times;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One slot of a post (a carousel has several). Holds the storage key, never bytes. */
@Entity
@Table(name = "post_media")
public class PostMedia {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "blurhash", length = 200)
    private String blurhash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected PostMedia() {
    }

    public PostMedia(int position, String storageKey, String mimeType, long sizeBytes,
                     Integer width, Integer height, Integer durationMs, String blurhash) {
        this.position = position;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.durationMs = durationMs;
        this.blurhash = blurhash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return id != null && id.equals(((PostMedia) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    void setPost(Post post) {
        this.post = post;
    }

    public int getPosition() {
        return position;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getBlurhash() {
        return blurhash;
    }

    public boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video/");
    }
}
