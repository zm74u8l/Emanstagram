package com.emanstagram.config;

import com.emanstagram.realtime.StompAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket wiring for realtime chat, typing, presence and notifications.
 *
 * <p>Plain STOMP over a native WebSocket at {@code /ws}; every supported
 * browser has WebSocket, so the SockJS fallback (previously registered on the
 * same path) is gone. The client authenticates in the STOMP CONNECT frame
 * with the same bearer token it uses for REST (see {@link StompAuthInterceptor}),
 * because browsers cannot set headers on the WebSocket handshake itself.
 *
 * <p>The simple in-memory broker suits a single instance. Scaling out means
 * swapping {@code enableSimpleBroker} for {@code enableStompBrokerRelay}
 * (RabbitMQ, or Redis via a bridge), with nothing else in this class changing.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Client-to-server destinations, e.g. {@code /app/conversations/{id}/typing}. */
    public static final String APP_PREFIX = "/app";

    /**
     * Server-to-user destinations. A client subscribes to
     * {@code /user/queue/events} and Spring routes it to that user's sessions only.
     */
    public static final String USER_PREFIX = "/user";

    private final StompAuthInterceptor authInterceptor;
    private final EmanstagramProperties properties;

    public WebSocketConfig(StompAuthInterceptor authInterceptor, EmanstagramProperties properties) {
        this.authInterceptor = authInterceptor;
        this.properties = properties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
        heartbeat.setPoolSize(1);
        heartbeat.setThreadNamePrefix("ws-heartbeat-");
        heartbeat.initialize();

        registry.enableSimpleBroker("/queue")
                // Heartbeats let both ends notice a dead connection (a laptop
                // lid closing) within ~20s, which is what makes presence honest.
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeat);
        registry.setApplicationDestinationPrefixes(APP_PREFIX);
        registry.setUserDestinationPrefix(USER_PREFIX);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same-origin connections are always allowed. The CORS origins cover
        // the Vite dev server. Auth is a bearer token in the CONNECT frame,
        // not a cookie, so cross-site WebSocket hijacking has nothing to ride on.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(properties.cors().allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
