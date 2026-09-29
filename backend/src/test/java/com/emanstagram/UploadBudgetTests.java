package com.emanstagram;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The storage quota and daily upload budget, with tiny limits so the edges
 * are easy to hit. Test files are 16 bytes each (see {@link #WEBP}).
 */
@SpringBootTest(properties = {
        "emanstagram.limits.storage-quota-bytes=40",       // room for 2 files, not 3
        "emanstagram.limits.new-account-daily-upload-count=4",
        "emanstagram.limits.new-account-daily-upload-bytes=1000000"
})
class UploadBudgetTests extends ApiTestSupport {

    @Test
    void theStorageQuotaCapsWhatAnAccountHoldsAndFreesUpOnDelete() throws Exception {
        Account a = register("qta");
        UUID first = createPost(a, 2, "PUBLIC", null);                  // 32 of 40 bytes

        mvc.perform(as(a, multipart("/api/posts").file(image("third.webp"))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("STORAGE_QUOTA_EXCEEDED"));

        JsonNode usage = read(mvc.perform(as(a, get("/api/me/storage"))));
        assertThat(usage.path("usedBytes").asLong()).isEqualTo(32);
        assertThat(usage.path("quotaBytes").asLong()).isEqualTo(40);

        mvc.perform(as(a, delete("/api/posts/" + first))).andExpect(status().isNoContent());
        createPost(a, 1, "PUBLIC", null);                               // fits again
    }

    @Test
    void theDailyBudgetCountsDeletedUploadsSoDeleteLoopsDontEscapeIt() throws Exception {
        Account a = register("dly");
        UUID p1 = createPost(a, 2, "PUBLIC", null);
        mvc.perform(as(a, delete("/api/posts/" + p1)));
        UUID p2 = createPost(a, 2, "PUBLIC", null);                     // 4 of 4 files today
        mvc.perform(as(a, delete("/api/posts/" + p2)));

        // Storage is empty again, but the day's budget is spent.
        mvc.perform(as(a, multipart("/api/posts").file(image("again.webp"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("DAILY_UPLOAD_LIMIT"));

        JsonNode usage = read(mvc.perform(as(a, get("/api/me/storage"))));
        assertThat(usage.path("usedBytes").asLong()).isZero();
        assertThat(usage.path("uploadsToday").asInt()).isEqualTo(4);
        assertThat(usage.path("newAccount").asBoolean()).isTrue();
    }

    @Test
    void avatarsCountTowardsTheQuota() throws Exception {
        Account a = register("avq");
        createPost(a, 2, "PUBLIC", null);                               // 32 of 40
        mvc.perform(as(a, multipart("/api/me/avatar")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "a.webp", "image/webp", WEBP))
                        .with(r -> { r.setMethod("PUT"); return r; })))
                .andExpect(status().isPayloadTooLarge());
    }
}
