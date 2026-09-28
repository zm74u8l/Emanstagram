package com.emanstagram.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DirectConversationKeyRepository
        extends JpaRepository<DirectConversationKey, DirectConversationKey.Key> {

    Optional<DirectConversationKey> findByUserLowAndUserHigh(UUID userLow, UUID userHigh);

    Optional<DirectConversationKey> findByConversationId(UUID conversationId);
}
