package com.emanstagram.post;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostMediaRepository extends JpaRepository<PostMedia, UUID> {

    /** All media for a page of posts in one query, in carousel order. */
    @Query("SELECT m FROM PostMedia m WHERE m.post.id IN :postIds ORDER BY m.post.id, m.position")
    List<PostMedia> findForPosts(@Param("postIds") Collection<UUID> postIds);

    /** Cover images of the newest public posts, for the signed-out login mosaic. */
    @Query("""
            SELECT m FROM PostMedia m JOIN m.post p
            WHERE p.visibility = com.emanstagram.post.PostVisibility.PUBLIC
              AND m.position = 0
              AND m.mimeType LIKE 'image/%'
            ORDER BY p.createdAt DESC
            """)
    List<PostMedia> publicCovers(Pageable pageable);
}
