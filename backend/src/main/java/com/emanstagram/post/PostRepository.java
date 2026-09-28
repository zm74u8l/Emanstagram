package com.emanstagram.post;

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

/**
 * Every list query is keyset-paginated on {@code (createdAt, id)} descending:
 * the caller passes the last row of the previous page (or
 * {@code Cursor.startDescending()} for the first page) and a limit of page
 * size + 1.
 */
public interface PostRepository extends JpaRepository<Post, UUID> {

    @EntityGraph(attributePaths = {"author"})
    Optional<Post> findWithAuthorById(UUID id);

    /** Home feed: my posts, plus non-private posts from people I follow. */
    @EntityGraph(attributePaths = {"author"})
    @Query("""
            SELECT p FROM Post p
            WHERE (p.author.id = :me
                   OR (p.visibility <> com.emanstagram.post.PostVisibility.PRIVATE
                       AND p.author.id IN (SELECT f.followeeId FROM Follow f WHERE f.followerId = :me)))
              AND (p.createdAt < :ts OR (p.createdAt = :ts AND p.id < :id))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> feed(@Param("me") UUID me, @Param("ts") Instant ts, @Param("id") UUID id,
                    Pageable pageable);

    /** Explore: public posts from everyone except me and anyone blocked either way. */
    @EntityGraph(attributePaths = {"author"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.visibility = com.emanstagram.post.PostVisibility.PUBLIC
              AND p.author.id <> :me
              AND p.author.id NOT IN :hidden
              AND (p.createdAt < :ts OR (p.createdAt = :ts AND p.id < :id))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> explore(@Param("me") UUID me, @Param("hidden") Collection<UUID> hidden,
                       @Param("ts") Instant ts, @Param("id") UUID id, Pageable pageable);

    /** A profile grid, restricted to the visibilities the viewer may see. */
    @EntityGraph(attributePaths = {"author"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.author.id = :author
              AND p.visibility IN :visibilities
              AND (p.createdAt < :ts OR (p.createdAt = :ts AND p.id < :id))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> byAuthor(@Param("author") UUID author,
                        @Param("visibilities") Collection<PostVisibility> visibilities,
                        @Param("ts") Instant ts, @Param("id") UUID id, Pageable pageable);

    /**
     * Candidate posts for a hashtag. LIKE is only a prefilter (#cat also
     * matches #caterpillar); the service keeps exact tag matches.
     */
    @EntityGraph(attributePaths = {"author"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.visibility = com.emanstagram.post.PostVisibility.PUBLIC
              AND lower(p.caption) LIKE :pattern
              AND p.author.id NOT IN :hidden
              AND (p.createdAt < :ts OR (p.createdAt = :ts AND p.id < :id))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> taggedCandidates(@Param("pattern") String pattern,
                                @Param("hidden") Collection<UUID> hidden,
                                @Param("ts") Instant ts, @Param("id") UUID id, Pageable pageable);

    /** Recent public captions containing a hashtag prefix, for search suggestions. */
    @Query("""
            SELECT p.caption FROM Post p
            WHERE p.visibility = com.emanstagram.post.PostVisibility.PUBLIC
              AND lower(p.caption) LIKE :pattern
            ORDER BY p.createdAt DESC
            """)
    List<String> captionsMatching(@Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id IN :ids")
    List<Post> findAllWithAuthorByIdIn(@Param("ids") Collection<UUID> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.id = :id")
    void adjustLikeCount(@Param("id") UUID id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :id")
    void adjustCommentCount(@Param("id") UUID id, @Param("delta") int delta);

    @Query("SELECT p.likeCount FROM Post p WHERE p.id = :id")
    Optional<Integer> likeCountOf(@Param("id") UUID id);
}
