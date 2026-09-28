package com.emanstagram.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @EntityGraph(attributePaths = {"actor"})
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientId = :me
              AND (n.createdAt < :ts OR (n.createdAt = :ts AND n.id < :id))
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<Notification> page(@Param("me") UUID me, @Param("ts") Instant ts, @Param("id") UUID id,
                            Pageable pageable);

    long countByRecipientIdAndReadFalse(UUID recipientId);

    /** Used to collapse repeat likes/follows from the same person into one entry. */
    @Query("""
            SELECT COUNT(n) > 0 FROM Notification n
            WHERE n.recipientId = :recipient AND n.actor.id = :actor AND n.kind = :kind
              AND ((:post IS NULL AND n.postId IS NULL) OR n.postId = :post)
              AND ((:comment IS NULL AND n.commentId IS NULL) OR n.commentId = :comment)
            """)
    boolean existsSame(@Param("recipient") UUID recipient, @Param("actor") UUID actor,
                       @Param("kind") NotificationKind kind, @Param("post") UUID post,
                       @Param("comment") UUID comment);

    /** Retracts the notification when the like or follow that caused it is undone. */
    @Modifying
    @Query("""
            DELETE FROM Notification n
            WHERE n.recipientId = :recipient AND n.actor.id = :actor AND n.kind = :kind
              AND ((:post IS NULL AND n.postId IS NULL) OR n.postId = :post)
              AND ((:comment IS NULL AND n.commentId IS NULL) OR n.commentId = :comment)
            """)
    int retract(@Param("recipient") UUID recipient, @Param("actor") UUID actor,
                @Param("kind") NotificationKind kind, @Param("post") UUID post,
                @Param("comment") UUID comment);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientId = :me AND n.read = false")
    int markAllRead(@Param("me") UUID me);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.postId = :post")
    void deleteByPost(@Param("post") UUID post);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.commentId IN :ids")
    void deleteByComments(@Param("ids") Collection<UUID> ids);
}
