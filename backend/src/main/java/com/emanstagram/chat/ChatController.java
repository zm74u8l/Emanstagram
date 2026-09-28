package com.emanstagram.chat;

import com.emanstagram.chat.dto.ChatDtos.*;
import com.emanstagram.common.CurrentUser;
import com.emanstagram.common.CursorPage;
import com.emanstagram.realtime.PresenceTracker;
import com.emanstagram.realtime.PresenceTracker.PresenceEvent;
import com.emanstagram.realtime.StompAuthInterceptor.StompPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chat;
    private final PresenceTracker presence;
    private final CurrentUser currentUser;

    public ChatController(ChatService chat, PresenceTracker presence, CurrentUser currentUser) {
        this.chat = chat;
        this.presence = presence;
        this.currentUser = currentUser;
    }

    @GetMapping("/conversations")
    public List<ConversationResponse> inbox() {
        return chat.inbox(currentUser.require());
    }

    @GetMapping("/conversations/unread-count")
    public UnreadCount unread() {
        return chat.unreadCount(currentUser.require());
    }

    @GetMapping("/conversations/{id}")
    public ConversationResponse get(@PathVariable UUID id) {
        return chat.get(id, currentUser.require());
    }

    @PostMapping("/conversations/direct")
    public ConversationResponse direct(@Valid @RequestBody DirectRequest request) {
        return chat.openDirect(request.userId(), currentUser.require());
    }

    @PostMapping("/conversations/group")
    public ResponseEntity<ConversationResponse> group(@Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.createGroup(request, currentUser.require()));
    }

    @PatchMapping("/conversations/{id}")
    public ConversationResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        return chat.rename(id, request, currentUser.require());
    }

    @PostMapping("/conversations/{id}/members")
    public ConversationResponse addMembers(@PathVariable UUID id, @Valid @RequestBody AddMembersRequest request) {
        return chat.addMembers(id, request, currentUser.require());
    }

    @DeleteMapping("/conversations/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        chat.removeMember(id, userId, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/messages")
    public CursorPage<MessageResponse> history(@PathVariable UUID id,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit) {
        return chat.history(id, currentUser.require(), cursor, limit);
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageResponse> send(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.send(id, request, currentUser.require()));
    }

    @PostMapping(path = "/conversations/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> attach(@PathVariable UUID id,
                                                  @RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "body", required = false) String body,
                                                  @RequestParam(value = "replyToId", required = false) UUID replyToId,
                                                  @RequestParam(value = "clientId", required = false) String clientId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chat.sendAttachment(id, file, body, replyToId, clientId, currentUser.require()));
    }

    @PostMapping("/conversations/{id}/read")
    public ReadEvent read(@PathVariable UUID id) {
        return chat.markRead(id, currentUser.require());
    }

    @PatchMapping("/messages/{id}")
    public MessageResponse edit(@PathVariable UUID id, @Valid @RequestBody EditMessageRequest request) {
        return chat.edit(id, request, currentUser.require());
    }

    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable UUID id) {
        chat.delete(id, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/presence")
    public List<PresenceEvent> presence(@RequestParam List<UUID> ids) {
        currentUser.require();
        return presence.snapshot(ids.stream().limit(100).toList());
    }

    /** STOMP: {@code SEND /app/conversations/{id}/typing} with {@code {"typing": true}}. */
    @MessageMapping("/conversations/{id}/typing")
    public void typing(@DestinationVariable UUID id, @Payload Map<String, Object> payload, Principal principal) {
        if (principal instanceof StompPrincipal p) {
            chat.typing(id, p.userId(), Boolean.TRUE.equals(payload.get("typing")));
        }
    }
}
