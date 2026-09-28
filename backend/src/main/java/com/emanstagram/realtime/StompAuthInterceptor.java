package com.emanstagram.realtime;

import com.emanstagram.auth.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

/**
 * Authenticates STOMP sessions and fences off what they may subscribe to.
 *
 * <ul>
 *   <li>CONNECT must carry {@code Authorization: Bearer <access token>}; the
 *       session's principal becomes the user id, which is what
 *       {@code convertAndSendToUser} addresses.</li>
 *   <li>SUBSCRIBE is limited to the caller's own {@code /user/queue/...}
 *       destinations, so no one can listen in on a broadcast topic.</li>
 *   <li>SEND requires an authenticated session.</li>
 * </ul>
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    /** The session principal: {@code getName()} is the user id. */
    public record StompPrincipal(UUID userId, String username) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }

    private final JwtService jwtService;

    public StompAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            String header = accessor.getFirstNativeHeader("Authorization");
            String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
            Claims claims = token == null ? null : jwtService.parseAccessToken(token).orElse(null);
            if (claims == null) {
                throw new MessagingException("UNAUTHENTICATED");
            }
            accessor.setUser(new StompPrincipal(UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class)));
            return message;
        }

        if (command == StompCommand.SUBSCRIBE) {
            requireUser(accessor);
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/user/queue/")) {
                throw new MessagingException("FORBIDDEN_DESTINATION");
            }
        } else if (command == StompCommand.SEND) {
            requireUser(accessor);
        }
        return message;
    }

    private static void requireUser(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof StompPrincipal)) {
            throw new MessagingException("UNAUTHENTICATED");
        }
    }
}
