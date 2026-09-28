package com.emanstagram.moderation;

import com.emanstagram.common.Times;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A user's report of an account, post or comment, awaiting moderator review. */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Column(name = "target_user_id", updatable = false)
    private UUID targetUserId;

    @Column(name = "post_id", updatable = false)
    private UUID postId;

    @Column(name = "comment_id", updatable = false)
    private UUID commentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20, updatable = false)
    private ReportReason reason;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Times.now();

    protected Report() {
    }

    public Report(UUID reporterId, UUID targetUserId, UUID postId, UUID commentId,
                  ReportReason reason, String details) {
        this.reporterId = reporterId;
        this.targetUserId = targetUserId;
        this.postId = postId;
        this.commentId = commentId;
        this.reason = reason;
        this.details = details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        return id != null && id.equals(((Report) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getCommentId() {
        return commentId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
