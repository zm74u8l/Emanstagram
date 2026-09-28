package com.emanstagram.post;

import com.emanstagram.common.Times;
import com.emanstagram.user.User;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A post: a caption plus one or more media items. Media bytes live in
 * Supabase Storage; {@link PostMedia} holds only the object keys.
 */
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(name = "caption", length = 2200)
    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private PostKind kind = PostKind.IMAGE;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private PostVisibility visibility = PostVisibility.PUBLIC;

    @Column(name = "location", length = 160)
    private String location;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    @Column(name = "edited_at")
    private Instant editedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PostMedia> media = new ArrayList<>();

    protected Post() {
    }

    public Post(User author, String caption, PostKind kind, PostVisibility visibility, String location) {
        this.author = author;
        this.caption = caption;
        this.kind = kind;
        this.visibility = visibility;
        this.location = location;
    }

    public void addMedia(PostMedia item) {
        item.setPost(this);
        media.add(item);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return id != null && id.equals(((Post) o).id);
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

    /** The author's id without initialising the lazy proxy. */
    public UUID getAuthorId() {
        return author.getId();
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public PostKind getKind() {
        return kind;
    }

    public PostVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(PostVisibility visibility) {
        this.visibility = visibility;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Instant editedAt) {
        this.editedAt = editedAt;
    }

    public List<PostMedia> getMedia() {
        return media;
    }
}
