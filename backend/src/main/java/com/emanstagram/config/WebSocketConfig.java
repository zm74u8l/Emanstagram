package com.emanstagram.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket wiring for real-time chat.
 *
 * <p>Uses STOMP over SockJS. The simple in-memory broker is right for a
 * single-instance deployment; if the backend is ever scaled horizontally,
 * swap {@code enableSimpleBroker} for {@code enableStompBrokerRelay} backed by
 * Redis and this class is otherwise unchanged.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Client subscribes here for messages destined to them. */
    public static final String USER_QUEUE_PREFIX = "/user/queue";

    /** Client-to-server destinations. */
    public static final String APP_PREFIX = "/app";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes(APP_PREFIX);
        registry.setUserDestinationPrefix(USER_QUEUE_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Raw WebSocket, for non-browser clients and mobile.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
