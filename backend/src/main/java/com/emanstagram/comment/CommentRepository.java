package com.emanstagram.comment;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Threads read oldest-first, so both list queries page ascending. */
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @EntityGraph(attributePaths = {"author"})
    Optional<Comment> findWithAuthorById(UUID id);

    @EntityGraph(attributePaths = {"author"})
    @Query("""
            SELECT c FROM Comment c
            WHERE c.postId = :post AND c.parentId IS NULL
              AND c.author.id NOT IN :hidden
              AND (c.createdAt > :ts OR (c.createdAt = :ts AND c.id > :id))
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<Comment> topLevelPage(@Param("post") UUID post, @Param("hidden") Collection<UUID> hidden,
                               @Param("ts") Instant ts, @Param("id") UUID id, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    @Query("""
            SELECT c FROM Comment c
            WHERE c.parentId = :parent
              AND c.author.id NOT IN :hidden
              AND (c.createdAt > :ts OR (c.createdAt = :ts AND c.id > :id))
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<Comment> repliesPage(@Param("parent") UUID parent, @Param("hidden") Collection<UUID> hidden,
                              @Param("ts") Instant ts, @Param("id") UUID id, Pageable pageable);

    /** Rows of {@code [parentId, count]} for a page of top-level comments. */
    @Query("""
            SELECT c.parentId, COUNT(c) FROM Comment c
            WHERE c.parentId IN :parents
            GROUP BY c.parentId
            """)
    List<Object[]> replyCounts(@Param("parents") Collection<UUID> parents);

    @Query("SELECT c.id FROM Comment c WHERE c.parentId = :parent")
    List<UUID> replyIds(@Param("parent") UUID parent);

    @Query("SELECT c.id FROM Comment c WHERE c.postId = :post")
    List<UUID> idsForPost(@Param("post") UUID post);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.id IN :ids")
    void deleteAllByIdIn(@Param("ids") Collection<UUID> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + :delta WHERE c.id = :id")
    void adjustLikeCount(@Param("id") UUID id, @Param("delta") int delta);

    @Query("SELECT c.likeCount FROM Comment c WHERE c.id = :id")
    Optional<Integer> likeCountOf(@Param("id") UUID id);
}
