package com.emanstagram.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            SELECT u FROM User u
            WHERE (lower(u.username) LIKE lower(concat('%', :q, '%'))
                   OR lower(coalesce(u.displayName, '')) LIKE lower(concat('%', :q, '%')))
              AND u.id <> :currentUser
            ORDER BY u.followerCount DESC, u.username ASC
            """)
    Page<User> search(@Param("q") String query, @Param("currentUser") UUID currentUser, Pageable pageable);

    /**
     * Atomic counter maintenance. Kept as bulk updates so a popular account
     * never suffers lost updates from concurrent follow/unfollow requests.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.followerCount = u.followerCount + :delta WHERE u.id = :id")
    void adjustFollowerCount(@Param("id") UUID id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.followingCount = u.followingCount + :delta WHERE u.id = :id")
    void adjustFollowingCount(@Param("id") UUID id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.postCount = u.postCount + :delta WHERE u.id = :id")
    void adjustPostCount(@Param("id") UUID id, @Param("delta") int delta);

    /** Delete refresh tokens that expired more than 30 days ago. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredTokens(@Param("cutoff") Instant cutoff);
}
