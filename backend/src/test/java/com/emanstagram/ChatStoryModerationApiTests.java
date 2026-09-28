package com.emanstagram;

import com.emanstagram.story.StoryService;
import com.emanstagram.user.Role;
import com.emanstagram.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chat, stories and moderation through the HTTP layer. */
class ChatStoryModerationApiTests extends ApiTestSupport {

    @Autowired
    private UserRepository users;

    @Autowired
    private StoryService storyService;

    // ------------------------------------------------------------ chat

    @Test
    void openingADirectMessageTwiceReturnsTheSameConversation() throws Exception {
        Account a = register("da");
        Account b = register("db");

        String first = read(mvc.perform(as(a, withJson(post("/api/conversations/direct"), Map.of("userId", b.id())))))
                .path("id").asText();
        String again = read(mvc.perform(as(b, withJson(post("/api/conversations/direct"), Map.of("userId", a.id())))))
                .path("id").asText();
        assertThat(again).isEqualTo(first);
    }

    @Test
    void messagesFlowAndUnreadCountsTrackReading() throws Exception {
        Account a = register("ma");
        Account b = register("mb");
        String conv = read(mvc.perform(as(a, withJson(post("/api/conversations/direct"), Map.of("userId", b.id())))))
                .path("id").asText();

        JsonNode sent = read(mvc.perform(as(a, withJson(post("/api/conversations/" + conv + "/messages"),
                Map.of("body", "hey there", "clientId", "tmp-1")))).andExpect(status().isCreated()));
        assertThat(sent.path("clientId").asText()).isEqualTo("tmp-1");

        mvc.perform(as(b, get("/api/conversations/unread-count"))).andExpect(jsonPath("$.count").value(1));
        mvc.perform(as(a, get("/api/conversations/unread-count"))).andExpect(jsonPath("$.count").value(0));

        JsonNode reply = read(mvc.perform(as(b, withJson(post("/api/conversations/" + conv + "/messages"),
                Map.of("body", "hi!", "replyToId", sent.path("id").asText())))));
        assertThat(reply.at("/replyTo/body").asText()).isEqualTo("hey there");

        mvc.perform(as(b, post("/api/conversations/" + conv + "/read"))).andExpect(status().isOk());
        mvc.perform(as(b, get("/api/conversations/unread-count"))).andExpect(jsonPath("$.count").value(0));

        JsonNode history = read(mvc.perform(as(a, get("/api/conversations/" + conv + "/messages"))));
        assertThat(history.path("items")).hasSize(2);
        assertThat(history.at("/items/0/body").asText()).as("newest first").isEqualTo("hi!");

        JsonNode inbox = read(mvc.perform(as(a, get("/api/conversations"))));
        assertThat(inbox.at("/0/lastMessage/body").asText()).isEqualTo("hi!");
        assertThat(inbox.at("/0/members")).hasSize(2);
    }

    @Test
    void outsidersCannotReadOrWriteAConversation() throws Exception {
        Account a = register("oa");
        Account b = register("ob");
        Account snoop = register("sn");
        String conv = read(mvc.perform(as(a, withJson(post("/api/conversations/direct"), Map.of("userId", b.id())))))
                .path("id").asText();

        mvc.perform(as(snoop, get("/api/conversations/" + conv + "/messages"))).andExpect(status().isNotFound());
        mvc.perform(as(snoop, withJson(post("/api/conversations/" + conv + "/messages"), Map.of("body", "hi"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void blockedPeopleCannotMessageEachOther() throws Exception {
        Account a = register("xa");
        Account b = register("xb");
        String conv = read(mvc.perform(as(a, withJson(post("/api/conversations/direct"), Map.of("userId", b.id())))))
                .path("id").asText();
        mvc.perform(as(b, put("/api/users/" + a.id() + "/block")));

        mvc.perform(as(a, withJson(post("/api/conversations/" + conv + "/messages"), Map.of("body", "hello?"))))
                .andExpect(status().isForbidden());
        mvc.perform(as(a, withJson(post("/api/conversations/direct"), Map.of("userId", b.id()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedMessagesKeepTheirPlaceButLoseTheirContent() throws Exception {
        Account a = register("za");
        Account b = register("zb");
        String conv = read(mvc.perform(as(a, withJson(post("/api/conversations/direct"), Map.of("userId", b.id())))))
                .path("id").asText();
        String msg = read(mvc.perform(as(a, withJson(post("/api/conversations/" + conv + "/messages"),
                Map.of("body", "oops"))))).path("id").asText();

        mvc.perform(as(b, delete("/api/messages/" + msg))).andExpect(status().isNotFound());
        mvc.perform(as(a, delete("/api/messages/" + msg))).andExpect(status().isNoContent());

        JsonNode history = read(mvc.perform(as(b, get("/api/conversations/" + conv + "/messages"))));
        assertThat(history.at("/items/0/deletedAt").isMissingNode()).isFalse();
        assertThat(history.at("/items/0/body").isMissingNode()).isTrue();
    }

    @Test
    void groupsSupportAddingLeavingAndOwnershipHandover() throws Exception {
        Account owner = register("go");
        Account m1 = register("gm");
        Account m2 = register("gn");

        JsonNode group = read(mvc.perform(as(owner, withJson(post("/api/conversations/group"),
                Map.of("title", "Trip", "memberIds", List.of(m1.id()))))).andExpect(status().isCreated()));
        String conv = group.path("id").asText();
        assertThat(group.path("members")).hasSize(2);

        // Only admins remove others.
        mvc.perform(as(m1, delete("/api/conversations/" + conv + "/members/" + owner.id())))
                .andExpect(status().isForbidden());

        mvc.perform(as(m1, withJson(post("/api/conversations/" + conv + "/members"), Map.of("userIds", List.of(m2.id())))))
                .andExpect(jsonPath("$.members.length()").value(3));

        mvc.perform(as(owner, delete("/api/conversations/" + conv + "/members/" + owner.id())))
                .andExpect(status().isNoContent());
        JsonNode after = read(mvc.perform(as(m1, get("/api/conversations/" + conv))));
        assertThat(after.path("members").findValuesAsText("role")).contains("OWNER");

        JsonNode history = read(mvc.perform(as(m1, get("/api/conversations/" + conv + "/messages"))));
        assertThat(history.path("items").findValuesAsText("kind")).contains("SYSTEM");
    }

    @Test
    void directPairsAreOrderedTheWayPostgresOrdersUuids() {
        // 8… has its top bit set: negative as a Java long, but the larger UUID
        // to Postgres. UUID.compareTo gets this pair backwards.
        UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");
        UUID low = UUID.fromString("10000000-0000-0000-0000-000000000000");
        assertThat(high.compareTo(low)).isNegative();
        UUID[] pair = com.emanstagram.chat.ChatServiceAccess.orderedPair(high, low);
        assertThat(pair[0]).isEqualTo(low);
        assertThat(pair[1]).isEqualTo(high);
    }

    // ------------------------------------------------------------ stories

    @Test
    void storiesAppearInFollowersTraysAndTrackViews() throws Exception {
        Account author = register("sa");
        Account fan = register("sf");
        mvc.perform(as(fan, put("/api/users/" + author.id() + "/follow")));

        JsonNode story = read(mvc.perform(as(author, multipart("/api/stories")
                        .file(new MockMultipartFile("file", "s.webp", "image/webp", new byte[]{1, 2, 3}))
                        .param("caption", "sunrise")))
                .andExpect(status().isCreated()));
        String storyId = story.path("id").asText();

        JsonNode tray = read(mvc.perform(as(fan, get("/api/stories/feed"))));
        assertThat(tray.at("/0/user/username").asText()).isEqualTo(author.username());
        assertThat(tray.at("/0/hasUnseen").asBoolean()).isTrue();

        mvc.perform(as(fan, post("/api/stories/" + storyId + "/view"))).andExpect(status().isNoContent());
        mvc.perform(as(fan, post("/api/stories/" + storyId + "/view"))).andExpect(status().isNoContent());
        assertThat(read(mvc.perform(as(fan, get("/api/stories/feed")))).at("/0/hasUnseen").asBoolean()).isFalse();

        mvc.perform(as(fan, get("/api/stories/" + storyId + "/viewers"))).andExpect(status().isForbidden());
        JsonNode viewers = read(mvc.perform(as(author, get("/api/stories/" + storyId + "/viewers"))));
        assertThat(viewers).hasSize(1);
        JsonNode own = read(mvc.perform(as(author, get("/api/stories/feed"))));
        assertThat(own.at("/0/stories/0/viewCount").asLong()).isEqualTo(1);

        JsonNode profile = read(mvc.perform(as(fan, get("/api/users/username/" + author.username()))));
        assertThat(profile.path("hasActiveStory").asBoolean()).isTrue();
    }

    @Test
    void purgeRemovesOnlyExpiredStories() throws Exception {
        Account author = register("pe");
        mvc.perform(as(author, multipart("/api/stories")
                .file(new MockMultipartFile("file", "s.webp", "image/webp", new byte[]{1}))));
        assertThat(storyService.purgeExpired()).isZero();
        assertThat(read(mvc.perform(as(author, get("/api/stories/feed"))))).hasSize(1);
    }

    // ------------------------------------------------------------ moderation

    @Test
    void reportsReachModeratorsAndRemovingContentClearsThem() throws Exception {
        Account author = register("ra");
        Account reporter = register("rr");
        Account mod = register("rm");
        inTx(() -> users.findById(mod.id()).ifPresent(u -> u.setRole(Role.MODERATOR)));
        UUID postId = createPost(author, 1, "PUBLIC", "spammy");

        String first = read(mvc.perform(as(reporter, withJson(post("/api/reports"),
                Map.of("postId", postId, "reason", "SPAM")))).andExpect(status().isCreated())).path("id").asText();
        String dup = read(mvc.perform(as(reporter, withJson(post("/api/reports"),
                Map.of("postId", postId, "reason", "SPAM"))))).path("id").asText();
        assertThat(dup).as("re-reporting is a no-op").isEqualTo(first);

        // Ordinary users are kept out of the queue.
        mvc.perform(as(reporter, get("/api/admin/reports"))).andExpect(status().isForbidden());

        JsonNode queue = read(mvc.perform(as(mod, get("/api/admin/reports"))).andExpect(status().isOk()));
        assertThat(queue.at("/items/0/targetType").asText()).isEqualTo("POST");
        assertThat(queue.at("/items/0/post/author/username").asText()).isEqualTo(author.username());

        mvc.perform(as(mod, withJson(post("/api/admin/reports/" + first + "/resolve"),
                Map.of("action", "REMOVE_CONTENT")))).andExpect(status().isNoContent());
        mvc.perform(as(author, get("/api/posts/" + postId))).andExpect(status().isNotFound());
        mvc.perform(as(mod, get("/api/admin/stats"))).andExpect(jsonPath("$.open").value(0));
    }

    @Test
    void dismissingKeepsTheContent() throws Exception {
        Account author = register("dz");
        Account reporter = register("dr");
        Account admin = register("dm");
        inTx(() -> users.findById(admin.id()).ifPresent(u -> u.setRole(Role.ADMIN)));
        UUID postId = createPost(author, 1, "PUBLIC", null);
        String report = read(mvc.perform(as(reporter, withJson(post("/api/reports"),
                Map.of("postId", postId, "reason", "OTHER", "details", "not sure"))))).path("id").asText();

        mvc.perform(as(admin, withJson(post("/api/admin/reports/" + report + "/resolve"),
                Map.of("action", "DISMISS")))).andExpect(status().isNoContent());
        mvc.perform(as(author, get("/api/posts/" + postId))).andExpect(status().isOk());
        JsonNode resolved = read(mvc.perform(as(admin, get("/api/admin/reports").param("status", "resolved"))));
        assertThat(resolved.path("items").findValuesAsText("id")).contains(report);
    }
}
