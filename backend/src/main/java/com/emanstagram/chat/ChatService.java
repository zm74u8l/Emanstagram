package com.emanstagram.chat;

import com.emanstagram.chat.dto.ChatDtos.*;
import com.emanstagram.common.AfterCommit;
import com.emanstagram.common.ApiException;
import com.emanstagram.common.Cursor;
import com.emanstagram.common.CursorPage;
import com.emanstagram.common.Times;
import com.emanstagram.realtime.PresenceTracker;
import com.emanstagram.realtime.RealtimePublisher;
import com.emanstagram.social.AccessPolicy;
import com.emanstagram.storage.MediaValidationService;
import com.emanstagram.storage.StorageService;
import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import com.emanstagram.user.UserViews;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Direct messages and group chats.
 *
 * <p>Writes go over REST, so validation errors come back as ordinary HTTP
 * errors. Every write then fans out over WebSocket to all members, including
 * the sender's other tabs.
 */
@Service
public class ChatService {

    private static final int MAX_GROUP_SIZE = 32;
    private static final int INBOX_SIZE = 50;
    private static final Duration ATTACHMENT_TTL = Duration.ofHours(1);

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final DirectConversationKeyRepository directKeys;
    private final MessageRepository messages;
    private final UserRepository users;
    private final UserViews userViews;
    private final AccessPolicy access;
    private final StorageService storage;
    private final MediaValidationService validation;
    private final RealtimePublisher realtime;
    private final PresenceTracker presence;
    private final TransactionTemplate tx;

    public ChatService(ConversationRepository conversations, ConversationMemberRepository members,
                       DirectConversationKeyRepository directKeys, MessageRepository messages,
                       UserRepository users, UserViews userViews, AccessPolicy access,
                       StorageService storage, MediaValidationService validation,
                       RealtimePublisher realtime, PresenceTracker presence,
                       PlatformTransactionManager txManager) {
        this.conversations = conversations;
        this.members = members;
        this.directKeys = directKeys;
        this.messages = messages;
        this.users = users;
        this.userViews = userViews;
        this.access = access;
        this.storage = storage;
        this.validation = validation;
        this.realtime = realtime;
        this.presence = presence;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------
    // conversations
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConversationResponse> inbox(User me) {
        List<Conversation> rows = conversations.inbox(me.getId(), PageRequest.of(0, INBOX_SIZE));
        return assembleConversations(rows, me.getId());
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(UUID conversationId, User me) {
        Conversation c = requireMember(conversationId, me.getId());
        return assembleConversations(List.of(c), me.getId()).get(0);
    }

    /**
     * Returns the DM with {@code otherId}, creating it on first use.
     *
     * <p>If both people open the DM at the same instant, one insert loses on
     * the {@code direct_conversation_keys} primary key. That loser simply
     * reads the winner's row, so both land in the same conversation.
     */
    public ConversationResponse openDirect(UUID otherId, User me) {
        if (otherId.equals(me.getId())) {
            throw ApiException.badRequest("SELF_ACTION", "You can't message yourself.");
        }
        User other = users.findById(otherId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "That account doesn't exist."));
        if (access.blockedEitherWay(me.getId(), otherId)) {
            throw ApiException.forbidden("BLOCKED", "You can't message this account.");
        }

        UUID[] pair = orderedPair(me.getId(), other.getId());
        UUID conversationId;
        try {
            conversationId = tx.execute(status -> directKeys.findByUserLowAndUserHigh(pair[0], pair[1])
                    .map(DirectConversationKey::getConversationId)
                    .orElseGet(() -> {
                        Conversation c = conversations.save(new Conversation(ConversationKind.DIRECT, null, me.getId()));
                        directKeys.saveAndFlush(new DirectConversationKey(pair[0], pair[1], c.getId()));
                        members.save(new ConversationMember(c.getId(), me.getId(), ConversationMember.MEMBER));
                        members.save(new ConversationMember(c.getId(), other.getId(), ConversationMember.MEMBER));
                        return c.getId();
                    }));
        } catch (DataIntegrityViolationException race) {
            conversationId = tx.execute(status -> directKeys.findByUserLowAndUserHigh(pair[0], pair[1])
                    .map(DirectConversationKey::getConversationId).orElseThrow(() -> race));
        }
        UUID id = conversationId;
        return tx.execute(status -> get(id, me));
    }

    @Transactional
    public ConversationResponse createGroup(CreateGroupRequest request, User me) {
        Set<UUID> invitees = new LinkedHashSet<>(request.memberIds());
        invitees.remove(me.getId());
        if (invitees.isEmpty()) {
            throw ApiException.badRequest("NO_MEMBERS", "Add at least one other person.");
        }
        if (invitees.size() + 1 > MAX_GROUP_SIZE) {
            throw ApiException.badRequest("GROUP_TOO_LARGE", "Groups are limited to " + MAX_GROUP_SIZE + " people.");
        }
        Map<UUID, User> found = userViews.load(invitees);
        Set<UUID> hidden = access.hiddenFrom(me.getId());
        invitees.removeIf(id -> !found.containsKey(id) || hidden.contains(id));
        if (invitees.isEmpty()) {
            throw ApiException.badRequest("NO_MEMBERS", "None of those people can be added.");
        }

        Conversation c = conversations.save(new Conversation(ConversationKind.GROUP, request.title().trim(), me.getId()));
        members.save(new ConversationMember(c.getId(), me.getId(), ConversationMember.OWNER));
        invitees.forEach(id -> members.save(new ConversationMember(c.getId(), id, ConversationMember.MEMBER)));
        systemMessage(c, me.getUsername() + " created the group");
        return get(c.getId(), me);
    }

    @Transactional
    public ConversationResponse rename(UUID conversationId, RenameRequest request, User me) {
        Conversation c = requireGroupMember(conversationId, me.getId());
        c.setTitle(request.title().trim());
        systemMessage(c, me.getUsername() + " renamed the group to \"" + c.getTitle() + "\"");
        ConversationResponse response = get(conversationId, me);
        realtime.toUsers(members.memberIds(conversationId), "conversation.updated", response);
        return response;
    }

    @Transactional
    public ConversationResponse addMembers(UUID conversationId, AddMembersRequest request, User me) {
        Conversation c = requireGroupMember(conversationId, me.getId());
        Set<UUID> current = new HashSet<>(members.memberIds(conversationId));
        Set<UUID> hidden = access.hiddenFrom(me.getId());
        Map<UUID, User> found = userViews.load(request.userIds());

        List<String> added = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(request.userIds())) {
            User u = found.get(id);
            if (u == null || current.contains(id) || hidden.contains(id)) {
                continue;
            }
            if (current.size() + 1 > MAX_GROUP_SIZE) {
                throw ApiException.badRequest("GROUP_TOO_LARGE", "Groups are limited to " + MAX_GROUP_SIZE + " people.");
            }
            members.save(new ConversationMember(conversationId, id, ConversationMember.MEMBER));
            current.add(id);
            added.add(u.getUsername());
        }
        if (!added.isEmpty()) {
            systemMessage(c, me.getUsername() + " added " + String.join(", ", added));
        }
        ConversationResponse response = get(conversationId, me);
        realtime.toUsers(current, "conversation.updated", response);
        return response;
    }

    /**
     * Leaves a group (removing yourself) or removes someone else, which needs
     * the OWNER or ADMIN role. An owner who leaves hands the group to the
     * longest-standing member; the last one out deletes it.
     */
    @Transactional
    public void removeMember(UUID conversationId, UUID userId, User me) {
        Conversation c = requireGroupMember(conversationId, me.getId());
        ConversationMember actor = members.findByConversationIdAndUserId(conversationId, me.getId()).orElseThrow();
        boolean leaving = userId.equals(me.getId());
        if (!leaving && !actor.canManage()) {
            throw ApiException.forbidden("NOT_GROUP_ADMIN", "Only group admins can remove people.");
        }
        ConversationMember target = members.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.notFound("NOT_A_MEMBER", "That person isn't in this group."));
        List<UUID> before = members.memberIds(conversationId);
        members.delete(target);
        members.flush();

        List<ConversationMember> remaining = members.findByConversationId(conversationId);
        if (remaining.isEmpty()) {
            conversations.delete(c);
            return;
        }
        if (ConversationMember.OWNER.equals(target.getRole())) {
            remaining.stream().min(Comparator.comparing(ConversationMember::getJoinedAt))
                    .ifPresent(next -> next.setRole(ConversationMember.OWNER));
        }
        String who = users.findById(userId).map(User::getUsername).orElse("someone");
        systemMessage(c, leaving ? who + " left the group" : me.getUsername() + " removed " + who);
        realtime.toUsers(before, "conversation.removed", Map.of("conversationId", conversationId, "userId", userId));
    }

    // ------------------------------------------------------------------
    // messages
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CursorPage<MessageResponse> history(UUID conversationId, User me, String cursor, Integer limit) {
        requireMember(conversationId, me.getId());
        int size = CursorPage.clamp(limit == null ? 30 : limit);
        Cursor c = Cursor.decodeDescending(cursor);
        List<Message> rows = messages.historyPage(conversationId, c.createdAt(), c.id(), PageRequest.of(0, size + 1));
        return CursorPage.of(rows, size, m -> new Cursor(m.getCreatedAt(), m.getId()), this::assembleMessages);
    }

    @Transactional
    public MessageResponse send(UUID conversationId, SendMessageRequest request, User me) {
        Conversation c = requireCanSend(conversationId, me);
        UUID replyTo = validReplyTo(request.replyToId(), conversationId);
        Message m = messages.save(new Message(conversationId, me, MessageKind.TEXT, request.body().trim(), null, replyTo));
        return deliver(c, m, me, request.clientId());
    }

    /** An image or video, optionally with a caption. The file goes to the private bucket. */
    public MessageResponse sendAttachment(UUID conversationId, MultipartFile file, String body,
                                          UUID replyToId, String clientId, User me) {
        validation.validateChatAttachment(file);
        if (body != null && body.length() > 4000) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG", "Messages are limited to 4000 characters.");
        }
        // Membership and blocks are checked before spending time on the upload.
        tx.executeWithoutResult(status -> requireCanSend(conversationId, me));

        String key;
        try {
            key = storage.upload(StorageService.MESSAGES, conversationId, file.getBytes(),
                    file.getContentType(), validation.extensionFor(file.getContentType()));
        } catch (IOException ex) {
            throw ApiException.badRequest("UPLOAD_UNREADABLE", "That file could not be read. Please try again.");
        }
        try {
            return tx.execute(status -> {
                Conversation c = requireCanSend(conversationId, me);
                MessageKind kind = validation.isVideo(file.getContentType()) ? MessageKind.VIDEO : MessageKind.IMAGE;
                Message m = messages.save(new Message(conversationId, me, kind,
                        body == null || body.isBlank() ? null : body.trim(), key,
                        validReplyTo(replyToId, conversationId)));
                return deliver(c, m, me, clientId);
            });
        } catch (RuntimeException ex) {
            storage.delete(key);
            throw ex;
        }
    }

    @Transactional
    public MessageResponse edit(UUID messageId, EditMessageRequest request, User me) {
        Message m = ownMessage(messageId, me);
        if (m.getKind() != MessageKind.TEXT) {
            throw ApiException.badRequest("NOT_EDITABLE", "Only text messages can be edited.");
        }
        m.setBody(request.body().trim());
        m.setEditedAt(Times.now());
        MessageResponse response = assembleMessages(List.of(m)).get(0);
        realtime.toUsers(members.memberIds(m.getConversationId()), "message.updated", response);
        return response;
    }

    /**
     * Soft delete. The row stays so replies quoting it still render; the body
     * and attachment are withheld from every response from now on, and the
     * attachment object is removed from storage.
     */
    @Transactional
    public void delete(UUID messageId, User me) {
        Message m = ownMessage(messageId, me);
        m.setDeletedAt(Times.now());
        String key = m.getStorageKey();
        AfterCommit.run(() -> storage.delete(key));
        realtime.toUsers(members.memberIds(m.getConversationId()), "message.deleted",
                new DeletedEvent(m.getConversationId(), m.getId()));
    }

    @Transactional
    public ReadEvent markRead(UUID conversationId, User me) {
        ConversationMember member = members.findByConversationIdAndUserId(conversationId, me.getId())
                .orElseThrow(ChatService::conversationNotFound);
        Instant now = Times.now();
        member.setLastReadAt(now);
        ReadEvent event = new ReadEvent(conversationId, me.getId(), now);
        realtime.toUsers(members.memberIds(conversationId), "read", event);
        return event;
    }

    /** Relays a typing indicator to the other members. Nothing is stored. */
    @Transactional(readOnly = true)
    public void typing(UUID conversationId, UUID userId, boolean typing) {
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            return;
        }
        users.findById(userId).ifPresent(u -> {
            List<UUID> others = members.memberIds(conversationId).stream().filter(id -> !id.equals(userId)).toList();
            realtime.toUsers(others, "typing", new TypingEvent(conversationId, userViews.summary(u), typing));
        });
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(User me) {
        return new UnreadCount(messages.unreadConversationCount(me.getId()));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private MessageResponse deliver(Conversation c, Message m, User sender, String clientId) {
        c.setLastMessageAt(m.getCreatedAt());
        // Sending implies you've read everything up to your own message.
        members.findByConversationIdAndUserId(c.getId(), sender.getId())
                .ifPresent(member -> member.setLastReadAt(m.getCreatedAt()));
        MessageResponse response = assembleMessages(List.of(m)).get(0).withClientId(clientId);
        realtime.toUsers(members.memberIds(c.getId()), "message", response);
        return response;
    }

    private void systemMessage(Conversation c, String text) {
        Message m = messages.save(new Message(c.getId(), null, MessageKind.SYSTEM, text, null, null));
        c.setLastMessageAt(m.getCreatedAt());
        realtime.toUsers(members.memberIds(c.getId()), "message", assembleMessages(List.of(m)).get(0));
    }

    private Conversation requireMember(UUID conversationId, UUID userId) {
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            throw conversationNotFound();
        }
        return conversations.findById(conversationId).orElseThrow(ChatService::conversationNotFound);
    }

    private Conversation requireGroupMember(UUID conversationId, UUID userId) {
        Conversation c = requireMember(conversationId, userId);
        if (!c.isGroup()) {
            throw ApiException.badRequest("NOT_A_GROUP", "That only works in group chats.");
        }
        return c;
    }

    /** Membership, plus for a DM: neither side has blocked the other. */
    private Conversation requireCanSend(UUID conversationId, User me) {
        Conversation c = requireMember(conversationId, me.getId());
        if (!c.isGroup()) {
            boolean blocked = members.memberIds(conversationId).stream()
                    .filter(id -> !id.equals(me.getId()))
                    .anyMatch(other -> access.blockedEitherWay(me.getId(), other));
            if (blocked) {
                throw ApiException.forbidden("BLOCKED", "You can't message this account.");
            }
        }
        return c;
    }

    private UUID validReplyTo(UUID replyToId, UUID conversationId) {
        if (replyToId == null) {
            return null;
        }
        return messages.findById(replyToId)
                .filter(r -> r.getConversationId().equals(conversationId))
                .map(Message::getId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_REPLY", "You can only reply within the same chat."));
    }

    private Message ownMessage(UUID messageId, User me) {
        Message m = messages.findById(messageId)
                .filter(x -> me.getId().equals(x.getSenderId()) && !x.isDeleted())
                .orElseThrow(() -> ApiException.notFound("MESSAGE_NOT_FOUND", "That message isn't available."));
        if (!members.existsByConversationIdAndUserId(m.getConversationId(), me.getId())) {
            throw ApiException.notFound("MESSAGE_NOT_FOUND", "That message isn't available.");
        }
        return m;
    }

    /**
     * Orders a pair the way Postgres orders UUIDs (unsigned, byte by byte),
     * which is what {@code ck_direct_pair CHECK (user_low < user_high)} uses.
     * Java's {@code UUID.compareTo} compares signed longs and disagrees for
     * roughly half of all pairs; the lowercase hex strings sort correctly.
     */
    static UUID[] orderedPair(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) < 0 ? new UUID[]{a, b} : new UUID[]{b, a};
    }

    private List<ConversationResponse> assembleConversations(List<Conversation> rows, UUID me) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(Conversation::getId).toList();

        List<ConversationMember> allMembers = members.findByConversationIds(ids);
        Map<UUID, User> usersById = userViews.load(allMembers.stream().map(ConversationMember::getUserId).toList());
        Map<UUID, List<MemberView>> membersByConversation = new HashMap<>();
        for (ConversationMember cm : allMembers) {
            User u = usersById.get(cm.getUserId());
            if (u != null) {
                membersByConversation.computeIfAbsent(cm.getConversationId(), k -> new ArrayList<>())
                        .add(new MemberView(userViews.summary(u), cm.getRole(), cm.getLastReadAt(),
                                presence.isOnline(u.getId())));
            }
        }

        Map<UUID, MessageResponse> latest = new HashMap<>();
        List<Message> lastMessages = messages.latestIn(ids);
        List<MessageResponse> assembled = assembleMessages(lastMessages);
        for (MessageResponse r : assembled) {
            latest.putIfAbsent(r.conversationId(), r);
        }

        Map<UUID, Long> unread = new HashMap<>();
        for (Object[] row : messages.unreadCounts(me, ids)) {
            unread.put((UUID) row[0], (Long) row[1]);
        }

        List<ConversationResponse> result = new ArrayList<>(rows.size());
        for (Conversation c : rows) {
            result.add(new ConversationResponse(c.getId(), c.getKind(), c.getTitle(),
                    membersByConversation.getOrDefault(c.getId(), List.of()),
                    latest.get(c.getId()), unread.getOrDefault(c.getId(), 0L),
                    c.getLastMessageAt(), c.getCreatedAt()));
        }
        return result;
    }

    private List<MessageResponse> assembleMessages(List<Message> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> replyIds = new HashSet<>();
        List<String> keys = new ArrayList<>();
        for (Message m : rows) {
            if (m.getReplyToId() != null) {
                replyIds.add(m.getReplyToId());
            }
            if (m.getStorageKey() != null && !m.isDeleted()) {
                keys.add(m.getStorageKey());
            }
        }
        Map<UUID, Message> replies = new HashMap<>();
        if (!replyIds.isEmpty()) {
            messages.findAllById(replyIds).forEach(r -> replies.put(r.getId(), r));
        }
        Map<String, String> urls = storage.signedUrls(keys, ATTACHMENT_TTL);

        List<MessageResponse> result = new ArrayList<>(rows.size());
        for (Message m : rows) {
            ReplyPreview preview = null;
            Message r = m.getReplyToId() == null ? null : replies.get(m.getReplyToId());
            if (r != null) {
                preview = new ReplyPreview(r.getId(),
                        r.getSender() == null ? null : r.getSender().getUsername(),
                        r.getKind(), r.isDeleted() ? null : r.getBody(), r.isDeleted());
            }
            boolean deleted = m.isDeleted();
            result.add(new MessageResponse(m.getId(), m.getConversationId(),
                    m.getSender() == null ? null : userViews.summary(m.getSender()),
                    m.getKind(),
                    deleted ? null : m.getBody(),
                    deleted || m.getStorageKey() == null ? null : urls.get(m.getStorageKey()),
                    preview, m.getEditedAt(), m.getDeletedAt(), m.getCreatedAt(), null));
        }
        return result;
    }

    private static ApiException conversationNotFound() {
        return ApiException.notFound("CONVERSATION_NOT_FOUND", "That conversation isn't available.");
    }
}
