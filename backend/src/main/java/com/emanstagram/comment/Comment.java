package com.emanstagram.comment;

import com.emanstagram.common.Times;
import com.emanstagram.user.User;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A comment or a reply.
 *
 * <p>The schema allows unlimited nesting through {@code parent_id}, but the
 * service keeps threads two levels deep: a reply to a reply is re-parented to
 * the top-level comment, which is how every major app renders threads.
 */
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    @Column(name = "edited_at")
    private Instant editedAt;

    protected Comment() {
    }

    public Comment(UUID postId, User author, UUID parentId, String body) {
        this.postId = postId;
        this.author = author;
        this.parentId = parentId;
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return id != null && id.equals(((Comment) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public UUID getPostId() {
        return postId;
    }

    public User getAuthor() {
        return author;
    }

    public UUID getAuthorId() {
        return author.getId();
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getBody() {
        return body;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }
}
