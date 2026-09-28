package com.emanstagram.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMemberRepository
        extends JpaRepository<ConversationMember, ConversationMember.Key> {

    Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ConversationMember> findByConversationId(UUID conversationId);

    @Query("SELECT m FROM ConversationMember m WHERE m.conversationId IN :ids")
    List<ConversationMember> findByConversationIds(@Param("ids") Collection<UUID> ids);

    @Query("SELECT m.userId FROM ConversationMember m WHERE m.conversationId = :id")
    List<UUID> memberIds(@Param("id") UUID conversationId);

    /** Everyone I share a DM with, who should hear when I come online or go offline. */
    @Query("""
            SELECT DISTINCT other.userId FROM ConversationMember mine, ConversationMember other,
                                              Conversation c
            WHERE mine.userId = :me
              AND other.conversationId = mine.conversationId
              AND other.userId <> :me
              AND c.id = mine.conversationId
              AND c.kind = com.emanstagram.chat.ConversationKind.DIRECT
            """)
    List<UUID> directPartners(@Param("me") UUID me);
}
