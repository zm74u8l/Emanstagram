package com.emanstagram.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLike.Key> {

    /** Returns 1 when the like is new, 0 when it already existed. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO post_likes (user_id, post_id, created_at)
            VALUES (:user, :post, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("user") UUID user, @Param("post") UUID post);

    @Modifying
    @Query("DELETE FROM PostLike l WHERE l.userId = :user AND l.postId = :post")
    int deleteLike(@Param("user") UUID user, @Param("post") UUID post);

    @Query("SELECT l.postId FROM PostLike l WHERE l.userId = :user AND l.postId IN :postIds")
    Set<UUID> likedAmong(@Param("user") UUID user, @Param("postIds") Collection<UUID> postIds);

    @Query("""
            SELECT l FROM PostLike l
            WHERE l.postId = :post
              AND (l.createdAt < :ts OR (l.createdAt = :ts AND l.userId < :id))
            ORDER BY l.createdAt DESC, l.userId DESC
            """)
    List<PostLike> likersPage(@Param("post") UUID post, @Param("ts") Instant ts,
                              @Param("id") UUID id, Pageable pageable);

    @Modifying
    @Query("DELETE FROM PostLike l WHERE l.postId = :post")
    void deleteByPost(@Param("post") UUID post);
}
