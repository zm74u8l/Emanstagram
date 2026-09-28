package com.emanstagram.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** History, newest first; the client reverses each page for display. */
    @EntityGraph(attributePaths = {"sender"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversation
              AND (m.createdAt < :ts OR (m.createdAt = :ts AND m.id < :id))
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> historyPage(@Param("conversation") UUID conversation, @Param("ts") Instant ts,
                              @Param("id") UUID id, Pageable pageable);

    /** The latest message of each conversation in {@code ids}, for inbox previews. */
    @EntityGraph(attributePaths = {"sender"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId IN :ids
              AND m.createdAt = (SELECT MAX(m2.createdAt) FROM Message m2
                                 WHERE m2.conversationId = m.conversationId)
            """)
    List<Message> latestIn(@Param("ids") Collection<UUID> ids);

    /** Rows of {@code [conversationId, unreadCount]} for {@code me}. */
    @Query("""
            SELECT m.conversationId, COUNT(m) FROM Message m, ConversationMember cm
            WHERE cm.userId = :me
              AND cm.conversationId = m.conversationId
              AND m.conversationId IN :ids
              AND m.deletedAt IS NULL
              AND (m.sender IS NULL OR m.sender.id <> :me)
              AND (cm.lastReadAt IS NULL OR m.createdAt > cm.lastReadAt)
            GROUP BY m.conversationId
            """)
    List<Object[]> unreadCounts(@Param("me") UUID me, @Param("ids") Collection<UUID> ids);

    /** How many conversations have something I have not read, for the nav badge. */
    @Query("""
            SELECT COUNT(DISTINCT m.conversationId) FROM Message m, ConversationMember cm
            WHERE cm.userId = :me
              AND cm.conversationId = m.conversationId
              AND m.deletedAt IS NULL
              AND m.kind <> com.emanstagram.chat.MessageKind.SYSTEM
              AND (m.sender IS NULL OR m.sender.id <> :me)
              AND (cm.lastReadAt IS NULL OR m.createdAt > cm.lastReadAt)
            """)
    long unreadConversationCount(@Param("me") UUID me);
}
