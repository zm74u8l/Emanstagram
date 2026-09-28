package com.emanstagram.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** The inbox: conversations I belong to, most recently active first. */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.id IN (SELECT m.conversationId FROM ConversationMember m WHERE m.userId = :me)
            ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC
            """)
    List<Conversation> inbox(@Param("me") UUID me, Pageable pageable);
}
