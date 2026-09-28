package com.emanstagram.story;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface StoryViewRepository extends JpaRepository<StoryView, StoryView.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO story_views (story_id, user_id, viewed_at)
            VALUES (:story, :user, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("story") UUID story, @Param("user") UUID user);

    @Query("SELECT v.storyId FROM StoryView v WHERE v.userId = :user AND v.storyId IN :stories")
    Set<UUID> viewedAmong(@Param("user") UUID user, @Param("stories") Collection<UUID> stories);

    /** Rows of {@code [storyId, count]}. */
    @Query("SELECT v.storyId, COUNT(v) FROM StoryView v WHERE v.storyId IN :stories GROUP BY v.storyId")
    List<Object[]> viewCounts(@Param("stories") Collection<UUID> stories);

    List<StoryView> findByStoryIdOrderByViewedAtDesc(UUID storyId);

    @Modifying
    @Query("DELETE FROM StoryView v WHERE v.storyId IN :stories")
    void deleteByStories(@Param("stories") Collection<UUID> stories);
}
