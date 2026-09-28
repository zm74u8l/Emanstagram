package com.emanstagram.realtime;

import com.emanstagram.chat.ConversationMemberRepository;
import com.emanstagram.realtime.StompAuthInterceptor.StompPrincipal;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who is online from live WebSocket sessions.
 *
 * <p>A user is online while they have at least one connected session, so a
 * second tab closing doesn't flip them offline. Transitions are pushed only to
 * people they share a DM with, rather than broadcast to everyone.
 *
 * <p>In memory, which is correct for the single-instance deployment. Multiple
 * instances would move this to Redis alongside the broker relay.
 */
@Component
public class PresenceTracker {

    public record PresenceEvent(UUID userId, boolean online, Instant lastSeenAt) {
    }

    private final Map<UUID, Integer> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastSeen = new ConcurrentHashMap<>();

    private final ConversationMemberRepository members;
    private final RealtimePublisher realtime;

    public PresenceTracker(ConversationMemberRepository members, RealtimePublisher realtime) {
        this.members = members;
        this.realtime = realtime;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        userOf(event.getUser()).ifPresent(id -> {
            int count = sessions.merge(id, 1, Integer::sum);
            if (count == 1) {
                announce(id, true);
            }
        });
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        userOf(event.getUser()).ifPresent(id -> {
            Integer left = sessions.computeIfPresent(id, (k, v) -> v <= 1 ? null : v - 1);
            if (left == null) {
                lastSeen.put(id, Instant.now());
                announce(id, false);
            }
        });
    }

    public boolean isOnline(UUID userId) {
        return sessions.containsKey(userId);
    }

    public List<PresenceEvent> snapshot(Collection<UUID> userIds) {
        List<PresenceEvent> result = new ArrayList<>();
        for (UUID id : userIds) {
            result.add(new PresenceEvent(id, isOnline(id), lastSeen.get(id)));
        }
        return result;
    }

    private void announce(UUID userId, boolean online) {
        List<UUID> partners = members.directPartners(userId);
        if (!partners.isEmpty()) {
            realtime.toUsers(partners, "presence", new PresenceEvent(userId, online, lastSeen.get(userId)));
        }
    }

    private static Optional<UUID> userOf(Principal principal) {
        return principal instanceof StompPrincipal p ? Optional.of(p.userId()) : Optional.empty();
    }
}
