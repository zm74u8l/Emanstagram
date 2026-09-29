package com.emanstagram;

import com.emanstagram.post.PostRepository;
import com.emanstagram.user.Role;
import com.emanstagram.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Rate limits, file-content checks and account suspension. */
class AbuseProtectionTests extends ApiTestSupport {

    @Autowired
    private UserRepository users;

    @Autowired
    private PostRepository posts;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String registerBody(String username) throws Exception {
        return json.writeValueAsString(Map.of("username", username, "email", username + "@example.com",
                "password", "correct-horse-battery"));
    }

    // ------------------------------------------------------------ rate limits

    @Test
    void signUpsAreLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/register").with(from("203.0.113.7"))
                            .contentType(MediaType.APPLICATION_JSON).content(registerBody("rl" + UUID.randomUUID().toString().substring(0, 8))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/auth/register").with(from("203.0.113.7"))
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("rlblocked")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));

        // A different network is unaffected.
        mvc.perform(post("/api/auth/register").with(from("198.51.100.4"))
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("rlother")))
                .andExpect(status().isCreated());
    }

    @Test
    void aForgedForwardedForHeaderDoesNotDodgeTheLimit() throws Exception {
        // With no trusted proxy configured, X-Forwarded-For is ignored entirely.
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/register").with(from("203.0.113.9"))
                    .header("X-Forwarded-For", "10.0.0." + i)
                    .contentType(MediaType.APPLICATION_JSON).content(registerBody("xf" + UUID.randomUUID().toString().substring(0, 8))));
        }
        mvc.perform(post("/api/auth/register").with(from("203.0.113.9"))
                        .header("X-Forwarded-For", "10.9.9.9")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody("xfblocked")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordGuessingIsCutOff() throws Exception {
        Account victim = register("vic");
        String wrong = json.writeValueAsString(Map.of("identifier", victim.username(), "password", "nope-nope-nope"));
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").with(from("192.0.2.50"))
                            .contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(from("192.0.2.50"))
                        .contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void perAccountLimitsDoNotPunishOtherAccounts() throws Exception {
        Account spammer = register("spm");
        Account normal = register("nrm");
        UUID postId = createPost(normal, 1, "PUBLIC", null);

        for (int i = 0; i < 30; i++) {
            mvc.perform(as(spammer, withJson(post("/api/posts/" + postId + "/comments"), Map.of("body", "spam " + i))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(as(spammer, withJson(post("/api/posts/" + postId + "/comments"), Map.of("body", "one more"))))
                .andExpect(status().isTooManyRequests());
        // Same network (MockMvc), different account: still fine.
        mvc.perform(as(normal, withJson(post("/api/posts/" + postId + "/comments"), Map.of("body", "hello"))))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ file contents

    @Test
    void aFileIsJudgedByItsBytesNotItsLabel() throws Exception {
        Account a = register("snf");
        byte[] zip = {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0};
        mvc.perform(as(a, multipart("/api/posts").file(new MockMultipartFile("files", "cat.png", "image/png", zip))))
                .andExpect(status().isUnsupportedMediaType());

        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        mvc.perform(as(a, multipart("/api/posts").file(new MockMultipartFile("files", "x.jpg", "image/jpeg", html))))
                .andExpect(status().isUnsupportedMediaType());

        // A real WebP mislabelled as JPEG is accepted, and stored as what it really is.
        JsonNode created = read(mvc.perform(as(a, multipart("/api/posts")
                        .file(new MockMultipartFile("files", "photo.jpg", "image/jpeg", WEBP))))
                .andExpect(status().isCreated()));
        assertThat(created.at("/media/0/mimeType").asText()).isEqualTo("image/webp");
        assertThat(created.at("/media/0/url").asText()).endsWith(".webp");
    }

    @Test
    void avatarsAreSniffedToo() throws Exception {
        Account a = register("avs");
        mvc.perform(as(a, multipart("/api/me/avatar")
                        .file(new MockMultipartFile("file", "me.png", "image/png", "not an image at all".getBytes()))
                        .with(r -> { r.setMethod("PUT"); return r; })))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ------------------------------------------------------------ suspension

    @Test
    void suspendingAnAccountLocksItOutEverywhereAndCanWipeItsContent() throws Exception {
        Account admin = register("adm");
        Account abuser = register("abu");
        inTx(() -> users.findById(admin.id()).ifPresent(u -> u.setRole(Role.ADMIN)));
        createPost(abuser, 2, "PUBLIC", "junk");
        createPost(abuser, 1, "PUBLIC", "more junk");

        // Heaviest account first in the storage view.
        JsonNode heavy = read(mvc.perform(as(admin, get("/api/admin/accounts").param("sort", "STORAGE")
                .param("q", abuser.username()))).andExpect(status().isOk()));
        assertThat(heavy.at("/0/storageBytes").asLong()).isEqualTo(3L * WEBP.length);

        JsonNode result = read(mvc.perform(as(admin, withJson(post("/api/admin/accounts/" + abuser.id() + "/suspend"),
                Map.of("reason", "Spam uploads", "deleteContent", true)))).andExpect(status().isOk()));
        assertThat(result.path("postsDeleted").asInt()).isEqualTo(2);
        assertThat(posts.findAllByAuthorId(abuser.id())).isEmpty();

        // The still-valid access token is refused immediately.
        mvc.perform(as(abuser, get("/api/auth/me")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
        // Signing in again is refused, with the reason.
        mvc.perform(withJson(post("/api/auth/login"),
                        Map.of("identifier", abuser.username(), "password", "correct-horse-battery")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("This account has been suspended: Spam uploads"));

        JsonNode suspended = read(mvc.perform(as(admin, get("/api/admin/accounts").param("sort", "SUSPENDED"))));
        assertThat(suspended.findValuesAsText("username")).contains(abuser.username());

        mvc.perform(as(admin, delete("/api/admin/accounts/" + abuser.id() + "/suspend"))).andExpect(status().isNoContent());
        mvc.perform(withJson(post("/api/auth/login"),
                        Map.of("identifier", abuser.username(), "password", "correct-horse-battery")))
                .andExpect(status().isOk());
    }

    @Test
    void moderatorsCannotSuspendAdminsAndUsersCannotSuspendAnyone() throws Exception {
        Account admin = register("ad2");
        Account mod = register("md2");
        Account user = register("us2");
        inTx(() -> {
            users.findById(admin.id()).ifPresent(u -> u.setRole(Role.ADMIN));
            users.findById(mod.id()).ifPresent(u -> u.setRole(Role.MODERATOR));
        });

        mvc.perform(as(mod, withJson(post("/api/admin/accounts/" + admin.id() + "/suspend"), Map.of("reason", "x"))))
                .andExpect(status().isForbidden());
        mvc.perform(as(user, withJson(post("/api/admin/accounts/" + mod.id() + "/suspend"), Map.of("reason", "x"))))
                .andExpect(status().isForbidden());
        mvc.perform(as(user, get("/api/admin/accounts"))).andExpect(status().isForbidden());
        mvc.perform(as(mod, withJson(post("/api/admin/accounts/" + mod.id() + "/suspend"), Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theIpCheckEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/public/ip").with(from("198.51.100.23")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip").value("198.51.100.23"));
    }
}
