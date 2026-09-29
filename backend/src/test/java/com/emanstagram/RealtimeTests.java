package com.emanstagram;

import com.emanstagram.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the real STOMP endpoint over a real socket: an unauthenticated
 * CONNECT is refused, and a message sent over REST arrives live on the
 * recipient's subscription.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class RealtimeTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private StorageService storage;

    @Autowired
    private com.emanstagram.abuse.RateLimiter rateLimiter;

    @org.junit.jupiter.api.BeforeEach
    void resetLimits() {
        rateLimiter.reset();
    }

    private record Account(String id, String token) {
    }

    @Test
    void connectWithoutATokenIsRejected() throws Exception {
        CompletableFuture<StompSession> future = stompClient().connectAsync(
                "ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() { });
        // The server answers CONNECT with an ERROR frame and closes the socket,
        // so the connect future never completes successfully.
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
        assertThat(future.isDone() && !future.isCompletedExceptionally())
                .as("an unauthenticated session must never be established")
                .isFalse();
    }

    @Test
    void aMessageArrivesLiveOnTheRecipientsSubscription() throws Exception {
        Account alice = register("rta");
        Account bob = register("rtb");

        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + bob.token());
        StompSession session = stompClient().connectAsync("ws://localhost:" + port + "/ws",
                new org.springframework.web.socket.WebSocketHttpHeaders(), connect,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((JsonNode) payload);
            }
        });
        Thread.sleep(300); // let the SUBSCRIBE land before sending

        RestClient rest = RestClient.create("http://localhost:" + port);
        JsonNode conv = rest.post().uri("/api/conversations/direct")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", bob.id()))
                .retrieve().body(JsonNode.class);
        rest.post().uri("/api/conversations/" + conv.path("id").asText() + "/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", "live?"))
                .retrieve().toBodilessEntity();

        JsonNode event;
        do {
            event = events.poll(5, TimeUnit.SECONDS);
            assertThat(event).as("a message event within 5s").isNotNull();
        } while (!"message".equals(event.path("type").asText()));
        assertThat(event.at("/data/body").asText()).isEqualTo("live?");
        assertThat(event.at("/data/sender/id").asText()).isEqualTo(alice.id());

        // Subscribing outside the caller's own queues is refused and drops the session.
        session.subscribe("/topic/everything", new StompSessionHandlerAdapter() { });
        Thread.sleep(500);
        assertThat(session.isConnected()).isFalse();
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(json);
        client.setMessageConverter(converter);
        return client;
    }

    private Account register(String prefix) {
        String username = prefix + UUID.randomUUID().toString().substring(0, 8).replace("-", "");
        JsonNode res = RestClient.create("http://localhost:" + port).post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "email", username + "@example.com",
                        "password", "correct-horse-battery"))
                .retrieve().body(JsonNode.class);
        return new Account(res.at("/user/id").asText(), res.path("accessToken").asText());
    }
}
