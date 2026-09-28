package com.emanstagram;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Follows, blocks, profiles, feeds and search. */
class SocialApiTests extends ApiTestSupport {

    @Test
    void followingIsIdempotentAndKeepsBothCountersRight() throws Exception {
        Account a = register("fa");
        Account b = register("fb");

        mvc.perform(as(a, put("/api/users/" + b.id() + "/follow"))).andExpect(jsonPath("$.followerCount").value(1));
        mvc.perform(as(a, put("/api/users/" + b.id() + "/follow"))).andExpect(jsonPath("$.followerCount").value(1));
        mvc.perform(as(a, get("/api/auth/me"))).andExpect(jsonPath("$.followingCount").value(1));

        JsonNode profile = read(mvc.perform(as(b, get("/api/users/username/" + a.username()))));
        assertThat(profile.path("followsYou").asBoolean()).isTrue();

        mvc.perform(as(a, delete("/api/users/" + b.id() + "/follow"))).andExpect(jsonPath("$.followerCount").value(0));
        mvc.perform(as(a, get("/api/auth/me"))).andExpect(jsonPath("$.followingCount").value(0));
    }

    @Test
    void youCannotFollowYourself() throws Exception {
        Account a = register("self");
        mvc.perform(as(a, put("/api/users/" + a.id() + "/follow"))).andExpect(status().isBadRequest());
    }

    @Test
    void blockingSeversFollowsAndHidesEverything() throws Exception {
        Account a = register("ba");
        Account b = register("bb");
        mvc.perform(as(a, put("/api/users/" + b.id() + "/follow")));
        mvc.perform(as(b, put("/api/users/" + a.id() + "/follow")));
        UUID bPost = createPost(b, 1, "PUBLIC", null);

        mvc.perform(as(a, put("/api/users/" + b.id() + "/block"))).andExpect(jsonPath("$.blocked").value(true));

        mvc.perform(as(a, get("/api/auth/me")))
                .andExpect(jsonPath("$.followingCount").value(0))
                .andExpect(jsonPath("$.followerCount").value(0));
        // The blocked person sees the blocker as a missing account...
        mvc.perform(as(b, get("/api/users/username/" + a.username()))).andExpect(status().isNotFound());
        // ...cannot follow back...
        mvc.perform(as(b, put("/api/users/" + a.id() + "/follow"))).andExpect(status().isForbidden());
        // ...and neither side sees the other's posts.
        mvc.perform(as(a, get("/api/posts/" + bPost))).andExpect(status().isNotFound());
        JsonNode explore = read(mvc.perform(as(a, get("/api/explore"))));
        explore.path("items").forEach(p -> assertThat(p.at("/author/id").asText()).isNotEqualTo(b.id().toString()));

        JsonNode blocked = read(mvc.perform(as(a, get("/api/me/blocked"))));
        assertThat(blocked).hasSize(1);
        mvc.perform(as(a, delete("/api/users/" + b.id() + "/block"))).andExpect(jsonPath("$.blocked").value(false));
        mvc.perform(as(b, get("/api/users/username/" + a.username()))).andExpect(status().isOk());
    }

    @Test
    void homeFeedShowsFollowedAccountsIncludingFollowersOnlyPosts() throws Exception {
        Account reader = register("rd");
        Account followed = register("fd");
        Account other = register("ot");
        UUID followersOnly = createPost(followed, 1, "FOLLOWERS", "for my people");
        UUID unrelated = createPost(other, 1, "PUBLIC", null);

        mvc.perform(as(reader, put("/api/users/" + followed.id() + "/follow")));
        JsonNode feed = read(mvc.perform(as(reader, get("/api/feed"))));
        assertThat(feed.path("items").findValuesAsText("id"))
                .contains(followersOnly.toString())
                .doesNotContain(unrelated.toString());
    }

    @Test
    void feedPaginatesWithoutDuplicatesOrGaps() throws Exception {
        Account author = register("pg");
        for (int i = 0; i < 5; i++) {
            createPost(author, 1, "PUBLIC", "post " + i);
        }
        JsonNode first = read(mvc.perform(as(author, get("/api/feed").param("limit", "2"))));
        JsonNode second = read(mvc.perform(as(author, get("/api/feed").param("limit", "2")
                .param("cursor", first.path("nextCursor").asText()))));
        JsonNode third = read(mvc.perform(as(author, get("/api/feed").param("limit", "2")
                .param("cursor", second.path("nextCursor").asText()))));

        assertThat(first.path("items")).hasSize(2);
        assertThat(second.path("items")).hasSize(2);
        assertThat(third.path("items")).hasSize(1);
        assertThat(third.path("nextCursor").isMissingNode() || third.path("nextCursor").isNull()).isTrue();
        assertThat(first.at("/items/0/caption").asText()).isEqualTo("post 4");
        assertThat(third.at("/items/0/caption").asText()).isEqualTo("post 0");
    }

    @Test
    void hashtagPagesMatchExactTagsOnly() throws Exception {
        Account a = register("tg");
        String tag = "cat" + System.nanoTime();
        UUID exact = createPost(a, 1, "PUBLIC", "sleepy #" + tag);
        UUID longer = createPost(a, 1, "PUBLIC", "hungry #" + tag + "erpillar");

        JsonNode page = read(mvc.perform(as(a, get("/api/tags/" + tag + "/posts"))));
        assertThat(page.path("items").findValuesAsText("id"))
                .contains(exact.toString())
                .doesNotContain(longer.toString());

        JsonNode search = read(mvc.perform(as(a, get("/api/search").param("q", "#" + tag))));
        assertThat(search.path("tags").findValuesAsText("tag")).contains(tag, tag + "erpillar");
    }

    @Test
    void searchFindsPeopleByUsername() throws Exception {
        Account seeker = register("sk");
        Account target = register("zephyrine");
        JsonNode res = read(mvc.perform(as(seeker, get("/api/search").param("q", "zephyr"))));
        assertThat(res.path("users").findValuesAsText("username")).contains(target.username());
    }

    @Test
    void profileEditsValidateAndNormalise() throws Exception {
        Account a = register("ed");
        Account taken = register("taken");

        mvc.perform(as(a, withJson(patch("/api/me"), Map.of(
                        "displayName", "Ed Example", "bio", "hi", "website", "example.com", "accentColor", "#EC4899"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.website").value("https://example.com"))
                .andExpect(jsonPath("$.accentColor").value("#ec4899"))
                .andExpect(jsonPath("$.effectiveName").value("Ed Example"));

        mvc.perform(as(a, withJson(patch("/api/me"), Map.of("username", taken.username().toUpperCase()))))
                .andExpect(status().isConflict());
        mvc.perform(as(a, withJson(patch("/api/me"), Map.of("website", "javascript:alert(1)"))))
                .andExpect(status().isBadRequest());
        mvc.perform(as(a, withJson(patch("/api/me"), Map.of("accentColor", "red"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usernameAvailabilityIsPublic() throws Exception {
        Account a = register("av");
        mvc.perform(get("/api/auth/username-available").param("username", a.username()))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/auth/username-available").param("username", "free_name_" + System.nanoTime() % 100000))
                .andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/api/auth/username-available").param("username", "no spaces"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void mosaicIsPublicAndOnlyShowsPublicPosts() throws Exception {
        Account a = register("mo");
        createPost(a, 1, "PRIVATE", null);
        createPost(a, 1, "PUBLIC", null);

        JsonNode tiles = read(mvc.perform(get("/api/public/mosaic")).andExpect(status().isOk()));
        assertThat(tiles.size()).isGreaterThanOrEqualTo(1);
        // Nothing identifying is exposed to signed-out visitors.
        tiles.forEach(t -> assertThat(t.has("author")).isFalse());
    }

    @Test
    void changingPasswordRevokesOtherSessions() throws Exception {
        Account a = register("pw");
        JsonNode other = read(mvc.perform(withJson(post("/api/auth/login"),
                Map.of("identifier", a.username(), "password", "correct-horse-battery"))));

        mvc.perform(as(a, withJson(post("/api/me/password"),
                        Map.of("currentPassword", "wrong-one", "newPassword", "another-good-one"))))
                .andExpect(status().isBadRequest());
        mvc.perform(as(a, withJson(post("/api/me/password"),
                        Map.of("currentPassword", "correct-horse-battery", "newPassword", "another-good-one"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mvc.perform(withJson(post("/api/auth/refresh"), Map.of("refreshToken", other.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
        mvc.perform(withJson(post("/api/auth/login"),
                        Map.of("identifier", a.username(), "password", "another-good-one")))
                .andExpect(status().isOk());
    }
}
