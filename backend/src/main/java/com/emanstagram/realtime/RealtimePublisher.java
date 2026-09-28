package com.emanstagram.realtime;

import com.emanstagram.common.AfterCommit;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Pushes events to signed-in clients over STOMP.
 *
 * <p>Every client subscribes to {@code /user/queue/events}. Each event is
 * {@code { type, ...payload }}, so a single subscription covers messages,
 * typing, receipts, presence and notifications and the client dispatches on
 * {@code type}.
 *
 * <p>Sends happen after the surrounding transaction commits, so a rolled-back
 * write is never announced.
 */
@Component
public class RealtimePublisher {

    public static final String EVENTS = "/queue/events";

    private final SimpMessagingTemplate template;

    public RealtimePublisher(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void toUser(UUID userId, String type, Object payload) {
        Map<String, Object> event = Map.of("type", type, "data", payload);
        AfterCommit.run(() -> template.convertAndSendToUser(userId.toString(), EVENTS, event));
    }

    public void toUsers(Collection<UUID> userIds, String type, Object payload) {
        Map<String, Object> event = Map.of("type", type, "data", payload);
        AfterCommit.run(() -> userIds.forEach(id ->
                template.convertAndSendToUser(id.toString(), EVENTS, event)));
    }
}
