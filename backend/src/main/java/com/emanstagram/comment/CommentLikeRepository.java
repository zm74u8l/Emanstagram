package com.emanstagram.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLike.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO comment_likes (user_id, comment_id, created_at)
            VALUES (:user, :comment, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("user") UUID user, @Param("comment") UUID comment);

    @Modifying
    @Query("DELETE FROM CommentLike l WHERE l.userId = :user AND l.commentId = :comment")
    int deleteLike(@Param("user") UUID user, @Param("comment") UUID comment);

    @Query("SELECT l.commentId FROM CommentLike l WHERE l.userId = :user AND l.commentId IN :ids")
    Set<UUID> likedAmong(@Param("user") UUID user, @Param("ids") Collection<UUID> ids);

    @Modifying
    @Query("DELETE FROM CommentLike l WHERE l.commentId IN :ids")
    void deleteByComments(@Param("ids") Collection<UUID> ids);
}
