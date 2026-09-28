package com.emanstagram.moderation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    @Query("""
            SELECT r FROM Report r
            WHERE r.resolved = :resolved
              AND (r.createdAt < :ts OR (r.createdAt = :ts AND r.id < :id))
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Report> queuePage(@Param("resolved") boolean resolved, @Param("ts") Instant ts,
                           @Param("id") UUID id, Pageable pageable);

    /** An open report by the same person about the same thing, so re-reporting is a no-op. */
    @Query("""
            SELECT r FROM Report r
            WHERE r.reporterId = :reporter AND r.resolved = false
              AND ((:user IS NULL AND r.targetUserId IS NULL) OR r.targetUserId = :user)
              AND ((:post IS NULL AND r.postId IS NULL) OR r.postId = :post)
              AND ((:comment IS NULL AND r.commentId IS NULL) OR r.commentId = :comment)
            """)
    List<Report> openDuplicate(@Param("reporter") UUID reporter, @Param("user") UUID user,
                               @Param("post") UUID post, @Param("comment") UUID comment);

    long countByResolvedFalse();

    @Modifying
    @Query("DELETE FROM Report r WHERE r.postId = :post")
    void deleteByPost(@Param("post") UUID post);

    @Modifying
    @Query("DELETE FROM Report r WHERE r.commentId IN :ids")
    void deleteByComments(@Param("ids") Collection<UUID> ids);
}
