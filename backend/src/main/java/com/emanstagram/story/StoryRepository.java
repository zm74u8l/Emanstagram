package com.emanstagram.story;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    /** Active stories by a set of authors, oldest first within each author (playback order). */
    @Query("""
            SELECT s FROM Story s JOIN FETCH s.author
            WHERE s.author.id IN :authors AND s.expiresAt > :now
            ORDER BY s.createdAt ASC
            """)
    List<Story> activeByAuthors(@Param("authors") Collection<UUID> authors, @Param("now") Instant now);

    @Query("SELECT COUNT(s) > 0 FROM Story s WHERE s.author.id = :author AND s.expiresAt > :now")
    boolean hasActive(@Param("author") UUID author, @Param("now") Instant now);

    List<Story> findByExpiresAtBefore(Instant cutoff);

    @Query("SELECT s FROM Story s WHERE s.author.id = :author")
    List<Story> findAllByAuthorId(@Param("author") UUID author);
}
