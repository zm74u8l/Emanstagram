package com.emanstagram;

import com.emanstagram.common.ApiException;
import com.emanstagram.config.EmanstagramProperties;
import com.emanstagram.config.EmanstagramProperties.Storage;
import com.emanstagram.storage.S3StorageService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The S3 provider's URL logic. No network: building the clients and
 * presigning are both local operations.
 */
class S3StorageServiceTests {

    private static S3StorageService service(String publicBaseUrl) {
        var s3 = new Storage.S3("https://account123.r2.cloudflarestorage.com", "auto", "AKIDEXAMPLE",
                "secret-example", "emanstagram-media", "emanstagram-private", publicBaseUrl, true);
        var storage = new Storage("s3", null, null, null, s3);
        return new S3StorageService(new EmanstagramProperties(null, null, storage, null, null, null));
    }

    @Test
    void publicMediaIsServedFromThePublicBaseUrl() {
        var storage = service("https://media.example.com/");
        assertThat(storage.publicUrl("posts/u1/abc.webp"))
                .isEqualTo("https://media.example.com/posts/u1/abc.webp");
        assertThat(storage.publicUrl("avatars/u1/x y.webp"))
                .as("path segments are encoded, slashes kept")
                .isEqualTo("https://media.example.com/avatars/u1/x%20y.webp");
        assertThat(storage.publicUrl(null)).isNull();
    }

    @Test
    void chatAttachmentsGetPresignedUrlsOnThePrivateBucket() {
        var storage = service("https://media.example.com");
        Map<String, String> urls = storage.signedUrls(
                List.of("messages/c1/pic.webp", "posts/u1/abc.webp"), Duration.ofHours(1));

        String signed = urls.get("messages/c1/pic.webp");
        assertThat(signed)
                .contains("emanstagram-private")
                .contains("messages/c1/pic.webp")
                .contains("X-Amz-Signature=")
                .contains("X-Amz-Expires=3600")
                .doesNotContain("media.example.com");

        // A public key never needs signing.
        assertThat(urls.get("posts/u1/abc.webp")).isEqualTo("https://media.example.com/posts/u1/abc.webp");
    }

    @Test
    void incompleteSettingsReportUnconfiguredInsteadOfCrashing() {
        var storage = service("");
        assertThat(storage.isConfigured()).isFalse();
        assertThat(storage.publicUrl("posts/u1/abc.webp")).isNull();
        assertThat(catchThrowable(storage::requireConfigured))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not configured");
    }
}
