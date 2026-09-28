package com.emanstagram.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, Block.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO blocks (blocker_id, blocked_id, created_at)
            VALUES (:blocker, :blocked, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("blocker") UUID blocker, @Param("blocked") UUID blocked);

    @Modifying
    @Query("DELETE FROM Block b WHERE b.blockerId = :blocker AND b.blockedId = :blocked")
    int deleteEdge(@Param("blocker") UUID blocker, @Param("blocked") UUID blocked);

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /** True when either person has blocked the other. */
    @Query("""
            SELECT COUNT(b) > 0 FROM Block b
            WHERE (b.blockerId = :a AND b.blockedId = :b)
               OR (b.blockerId = :b AND b.blockedId = :a)
            """)
    boolean existsEitherWay(@Param("a") UUID a, @Param("b") UUID b);

    /** Everyone {@code user} must not see: people they blocked plus people who blocked them. */
    @Query("""
            SELECT CASE WHEN b.blockerId = :user THEN b.blockedId ELSE b.blockerId END
            FROM Block b
            WHERE b.blockerId = :user OR b.blockedId = :user
            """)
    Set<UUID> hiddenFrom(@Param("user") UUID user);

    List<Block> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);
}
