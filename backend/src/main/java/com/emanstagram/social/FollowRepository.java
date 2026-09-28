package com.emanstagram.social;

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

public interface FollowRepository extends JpaRepository<Follow, Follow.Key> {

    /**
     * Idempotent insert. Returns 1 when a new edge was created and 0 when it
     * already existed, so counters move only on a real change even when two
     * requests race.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO follows (follower_id, followee_id, created_at)
            VALUES (:follower, :followee, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("follower") UUID follower, @Param("followee") UUID followee);

    @Modifying
    @Query("DELETE FROM Follow f WHERE f.followerId = :follower AND f.followeeId = :followee")
    int deleteEdge(@Param("follower") UUID follower, @Param("followee") UUID followee);

    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    @Query("SELECT f.followeeId FROM Follow f WHERE f.followerId = :viewer AND f.followeeId IN :ids")
    Set<UUID> followedAmong(@Param("viewer") UUID viewer, @Param("ids") Collection<UUID> ids);

    @Query("SELECT f.followeeId FROM Follow f WHERE f.followerId = :viewer")
    List<UUID> followeeIds(@Param("viewer") UUID viewer);

    @Query("""
            SELECT f FROM Follow f
            WHERE f.followeeId = :user
              AND (f.createdAt < :ts OR (f.createdAt = :ts AND f.followerId < :id))
            ORDER BY f.createdAt DESC, f.followerId DESC
            """)
    List<Follow> followersPage(@Param("user") UUID user, @Param("ts") Instant ts,
                               @Param("id") UUID id, Pageable pageable);

    @Query("""
            SELECT f FROM Follow f
            WHERE f.followerId = :user
              AND (f.createdAt < :ts OR (f.createdAt = :ts AND f.followeeId < :id))
            ORDER BY f.createdAt DESC, f.followeeId DESC
            """)
    List<Follow> followingPage(@Param("user") UUID user, @Param("ts") Instant ts,
                               @Param("id") UUID id, Pageable pageable);
}
