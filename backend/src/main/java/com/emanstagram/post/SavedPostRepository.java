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

public interface SavedPostRepository extends JpaRepository<SavedPost, SavedPost.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO saved_posts (user_id, post_id, created_at)
            VALUES (:user, :post, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("user") UUID user, @Param("post") UUID post);

    @Modifying
    @Query("DELETE FROM SavedPost s WHERE s.userId = :user AND s.postId = :post")
    int deleteSave(@Param("user") UUID user, @Param("post") UUID post);

    @Query("SELECT s.postId FROM SavedPost s WHERE s.userId = :user AND s.postId IN :postIds")
    Set<UUID> savedAmong(@Param("user") UUID user, @Param("postIds") Collection<UUID> postIds);

    @Query("""
            SELECT s FROM SavedPost s
            WHERE s.userId = :user
              AND (s.createdAt < :ts OR (s.createdAt = :ts AND s.postId < :id))
            ORDER BY s.createdAt DESC, s.postId DESC
            """)
    List<SavedPost> savedPage(@Param("user") UUID user, @Param("ts") Instant ts,
                              @Param("id") UUID id, Pageable pageable);

    @Modifying
    @Query("DELETE FROM SavedPost s WHERE s.postId = :post")
    void deleteByPost(@Param("post") UUID post);
}
