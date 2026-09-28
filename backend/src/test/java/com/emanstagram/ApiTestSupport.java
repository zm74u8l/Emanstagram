package com.emanstagram;

import com.emanstagram.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for API integration tests: the full Spring context on the H2
 * {@code local} profile, driven through MockMvc with real JWTs.
 *
 * <p>Supabase Storage is mocked so uploads need no network. Everything else,
 * including security, validation and the database, is real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public abstract class ApiTestSupport {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @MockitoBean
    protected StorageService storage;

    @Autowired
    private PlatformTransactionManager txManager;

    /** Runs repository writes that the test sets up directly, outside any endpoint. */
    protected void inTx(Runnable work) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> work.run());
    }

    /** A registered account and its access token. */
    protected record Account(UUID id, String username, String token) {
    }

    @BeforeEach
    void stubStorage() {
        when(storage.isConfigured()).thenReturn(true);
        when(storage.upload(anyString(), any(UUID.class), any(byte[].class), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0) + "/" + inv.getArgument(1) + "/"
                        + UUID.randomUUID() + "." + inv.getArgument(4));
        when(storage.publicUrl(anyString()))
                .thenAnswer(inv -> "https://cdn.test/" + inv.getArgument(0));
    }

    protected Account register(String prefix) throws Exception {
        String username = prefix + SEQ.incrementAndGet();
        String body = json.writeValueAsString(Map.of(
                "username", username,
                "email", username + "@example.com",
                "password", "correct-horse-battery"));
        JsonNode res = read(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()));
        return new Account(UUID.fromString(res.at("/user/id").asText()), username,
                res.path("accessToken").asText());
    }

    protected MockHttpServletRequestBuilder as(Account account, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + account.token());
    }

    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        String content = result.andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }

    protected static MockMultipartFile image(String name) {
        return new MockMultipartFile("files", name, "image/webp", new byte[]{1, 2, 3, 4});
    }

    /** Creates a post with {@code count} images and returns its id. */
    protected UUID createPost(Account author, int count, String visibility, String caption) throws Exception {
        var request = multipart("/api/posts");
        for (int i = 0; i < count; i++) {
            request.file(image("p" + i + ".webp"));
        }
        request.param("visibility", visibility);
        if (caption != null) {
            request.param("caption", caption);
        }
        JsonNode res = read(mvc.perform(as(author, request)).andExpect(status().isCreated()));
        return UUID.fromString(res.path("id").asText());
    }
}
