package com.emanstagram;

import com.emanstagram.social.FollowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Posts, likes, saves and comments, end to end through the HTTP layer. */
class PostApiTests extends ApiTestSupport {

    @Autowired
    private FollowRepository follows;

    @Test
    void creatingACarouselStoresMediaAndBumpsThePostCount() throws Exception {
        Account ana = register("ana");
        UUID postId = createPost(ana, 3, "PUBLIC", "three at once #film");

        JsonNode post = read(mvc.perform(as(ana, get("/api/posts/" + postId))).andExpect(status().isOk()));
        assertThat(post.path("kind").asText()).isEqualTo("CAROUSEL");
        assertThat(post.path("media")).hasSize(3);
        assertThat(post.at("/media/0/url").asText()).startsWith("https://cdn.test/posts/" + ana.id());

        mvc.perform(as(ana, get("/api/auth/me"))).andExpect(jsonPath("$.postCount").value(1));
    }

    @Test
    void uploadWithNoFilesIsRejectedWithAClearMessage() throws Exception {
        Account ana = register("ana");
        mvc.perform(as(ana, multipart("/api/posts").param("caption", "nothing attached")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void likesAreIdempotentAndCountedOnce() throws Exception {
        Account author = register("auth");
        Account fan = register("fan");
        UUID postId = createPost(author, 1, "PUBLIC", null);

        mvc.perform(as(fan, put("/api/posts/" + postId + "/like"))).andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(as(fan, put("/api/posts/" + postId + "/like"))).andExpect(jsonPath("$.likeCount").value(1));

        // The author hears about it exactly once.
        mvc.perform(as(author, get("/api/notifications/unread-count"))).andExpect(jsonPath("$.count").value(1));

        mvc.perform(as(fan, delete("/api/posts/" + postId + "/like"))).andExpect(jsonPath("$.likeCount").value(0));
        mvc.perform(as(fan, delete("/api/posts/" + postId + "/like"))).andExpect(jsonPath("$.likeCount").value(0));

        // Unliking retracts the notification.
        mvc.perform(as(author, get("/api/notifications/unread-count"))).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void followersOnlyPostsAreHiddenFromStrangers() throws Exception {
        Account author = register("auth");
        Account stranger = register("str");
        UUID postId = createPost(author, 1, "FOLLOWERS", null);

        mvc.perform(as(stranger, get("/api/posts/" + postId))).andExpect(status().isNotFound());

        inTx(() -> follows.insertIfAbsent(stranger.id(), author.id()));
        mvc.perform(as(stranger, get("/api/posts/" + postId))).andExpect(status().isOk());
    }

    @Test
    void privatePostsAreVisibleOnlyToTheAuthor() throws Exception {
        Account author = register("auth");
        Account follower = register("fol");
        inTx(() -> follows.insertIfAbsent(follower.id(), author.id()));
        UUID postId = createPost(author, 1, "PRIVATE", null);

        mvc.perform(as(follower, get("/api/posts/" + postId))).andExpect(status().isNotFound());
        mvc.perform(as(author, get("/api/posts/" + postId))).andExpect(status().isOk());

        JsonNode grid = read(mvc.perform(as(follower, get("/api/users/" + author.username() + "/posts"))));
        assertThat(grid.path("items")).isEmpty();
    }

    @Test
    void savesShowUpInTheSavedCollection() throws Exception {
        Account author = register("auth");
        Account reader = register("rdr");
        UUID postId = createPost(author, 1, "PUBLIC", null);

        mvc.perform(as(reader, put("/api/posts/" + postId + "/save"))).andExpect(jsonPath("$.saved").value(true));
        mvc.perform(as(reader, put("/api/posts/" + postId + "/save"))).andExpect(status().isOk());

        JsonNode saved = read(mvc.perform(as(reader, get("/api/me/saved"))));
        assertThat(saved.path("items")).hasSize(1);
        assertThat(saved.at("/items/0/savedByMe").asBoolean()).isTrue();
    }

    @Test
    void repliesToRepliesJoinTheRootThreadAndDeletionCascades() throws Exception {
        Account author = register("auth");
        Account a = register("ca");
        Account b = register("cb");
        UUID postId = createPost(author, 1, "PUBLIC", null);

        JsonNode root = read(mvc.perform(as(a, withJson(post("/api/posts/" + postId + "/comments"),
                Map.of("body", "first!")))).andExpect(status().isCreated()));
        String rootId = root.path("id").asText();

        JsonNode reply = read(mvc.perform(as(b, withJson(post("/api/posts/" + postId + "/comments"),
                Map.of("body", "@" + a.username() + " agreed", "parentId", rootId)))));
        JsonNode nested = read(mvc.perform(as(a, withJson(post("/api/posts/" + postId + "/comments"),
                Map.of("body", "thanks", "parentId", reply.path("id").asText())))));

        assertThat(nested.path("parentId").asText()).as("reply-to-reply is re-parented").isEqualTo(rootId);

        JsonNode top = read(mvc.perform(as(author, get("/api/posts/" + postId + "/comments"))));
        assertThat(top.path("items")).hasSize(1);
        assertThat(top.at("/items/0/replyCount").asLong()).isEqualTo(2);
        mvc.perform(as(author, get("/api/posts/" + postId))).andExpect(jsonPath("$.commentCount").value(3));

        // The post author may remove any comment on their post, replies included.
        mvc.perform(as(author, delete("/api/comments/" + rootId))).andExpect(status().isNoContent());
        mvc.perform(as(author, get("/api/posts/" + postId))).andExpect(jsonPath("$.commentCount").value(0));
    }

    @Test
    void strangersCannotDeleteSomeoneElsesComment() throws Exception {
        Account author = register("auth");
        Account commenter = register("cmt");
        Account stranger = register("str");
        UUID postId = createPost(author, 1, "PUBLIC", null);
        JsonNode c = read(mvc.perform(as(commenter, withJson(post("/api/posts/" + postId + "/comments"),
                Map.of("body", "hello")))));

        mvc.perform(as(stranger, delete("/api/comments/" + c.path("id").asText())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingAPostRemovesItsMediaAndDependants() throws Exception {
        Account author = register("auth");
        Account fan = register("fan");
        UUID postId = createPost(author, 2, "PUBLIC", null);
        mvc.perform(as(fan, put("/api/posts/" + postId + "/like")));
        mvc.perform(as(fan, put("/api/posts/" + postId + "/save")));
        mvc.perform(as(fan, withJson(post("/api/posts/" + postId + "/comments"), Map.of("body", "nice"))));

        mvc.perform(as(fan, delete("/api/posts/" + postId))).andExpect(status().isForbidden());
        mvc.perform(as(author, delete("/api/posts/" + postId))).andExpect(status().isNoContent());

        mvc.perform(as(author, get("/api/posts/" + postId))).andExpect(status().isNotFound());
        mvc.perform(as(author, get("/api/auth/me"))).andExpect(jsonPath("$.postCount").value(0));
        assertThat(read(mvc.perform(as(fan, get("/api/me/saved")))).path("items")).isEmpty();
        verify(storage).deleteAll(argThat(keys -> keys.size() == 2));
    }

    @Test
    void mentionsInACaptionNotifyTheMentionedUser() throws Exception {
        Account author = register("auth");
        Account friend = register("frd");
        createPost(author, 1, "PUBLIC", "shot by @" + friend.username());

        JsonNode list = read(mvc.perform(as(friend, get("/api/notifications"))));
        assertThat(list.at("/items/0/kind").asText()).isEqualTo("MENTION");
        assertThat(list.at("/items/0/actor/username").asText()).isEqualTo(author.username());
    }
}
