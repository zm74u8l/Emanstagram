package com.emanstagram.chat.dto;

import com.emanstagram.chat.ConversationKind;
import com.emanstagram.chat.MessageKind;
import com.emanstagram.user.dto.UserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payloads for conversations, messages and realtime chat events. */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record MemberView(UserSummary user, String role, Instant lastReadAt, boolean online) {
    }

    public record ConversationResponse(
            UUID id,
            ConversationKind kind,
            String title,
            List<MemberView> members,
            MessageResponse lastMessage,
            long unreadCount,
            Instant lastMessageAt,
            Instant createdAt
    ) {
    }

    /** The quoted message shown above a reply. */
    public record ReplyPreview(UUID id, String senderUsername, MessageKind kind, String body, boolean deleted) {
    }

    public record MessageResponse(
            UUID id,
            UUID conversationId,
            UserSummary sender,
            MessageKind kind,
            String body,
            String attachmentUrl,
            ReplyPreview replyTo,
            Instant editedAt,
            Instant deletedAt,
            Instant createdAt,
            /** Echo of the sender's temporary id, so an optimistic bubble can be matched up. */
            String clientId
    ) {
        public MessageResponse withClientId(String value) {
            return new MessageResponse(id, conversationId, sender, kind, body, attachmentUrl, replyTo,
                    editedAt, deletedAt, createdAt, value);
        }
    }

    public record SendMessageRequest(
            @NotBlank(message = "Write a message first")
            @Size(max = 4000, message = "Messages are limited to 4000 characters")
            String body,

            UUID replyToId,

            @Size(max = 64)
            String clientId
    ) {
    }

    public record EditMessageRequest(
            @NotBlank(message = "A message can't be empty")
            @Size(max = 4000, message = "Messages are limited to 4000 characters")
            String body
    ) {
    }

    public record DirectRequest(@NotNull UUID userId) {
    }

    public record CreateGroupRequest(
            @NotBlank(message = "Give the group a name")
            @Size(max = 120, message = "Group names are limited to 120 characters")
            String title,

            @NotEmpty(message = "Add at least one person")
            @Size(max = 31, message = "Groups are limited to 32 people")
            List<UUID> memberIds
    ) {
    }

    public record RenameRequest(
            @NotBlank(message = "Give the group a name")
            @Size(max = 120, message = "Group names are limited to 120 characters")
            String title
    ) {
    }

    public record AddMembersRequest(@NotEmpty List<UUID> userIds) {
    }

    public record TypingEvent(UUID conversationId, UserSummary user, boolean typing) {
    }

    public record ReadEvent(UUID conversationId, UUID userId, Instant lastReadAt) {
    }

    public record DeletedEvent(UUID conversationId, UUID messageId) {
    }

    public record UnreadCount(long count) {
    }
}
