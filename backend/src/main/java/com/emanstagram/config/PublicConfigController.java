package com.emanstagram.config;

import com.emanstagram.storage.MediaValidationService;
import com.emanstagram.storage.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Publishes client-safe configuration to unauthenticated callers.
 *
 * <p>Mirroring the upload limits here means the browser can reject an
 * oversized file before spending the user's bandwidth, instead of finding out
 * after a long upload. Nothing secret is exposed: the Supabase service role key
 * is never returned.
 */
@RestController
@RequestMapping("/api/config")
public class PublicConfigController {

    private final EmanstagramProperties properties;
    private final StorageService storageService;

    public PublicConfigController(EmanstagramProperties properties,
                                  StorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicConfig() {
        var media = properties.media();

        return ResponseEntity.ok(Map.of(
                "maxImageBytes", media.maxImageBytes(),
                "maxVideoBytes", media.maxVideoBytes(),
                "maxAvatarBytes", media.maxAvatarBytes(),
                "maxStoryVideoBytes", media.maxStoryVideoBytes(),
                "maxCarouselItems", media.maxCarouselItems(),
                "maxCaptionLength", media.maxCaptionLength(),
                "maxCommentLength", media.maxCommentLength(),
                "allowedImageTypes", media.allowedImageTypes(),
                "allowedVideoTypes", media.allowedVideoTypes(),
                // Lets the UI hide upload controls instead of failing at runtime.
                "storageEnabled", storageService.isConfigured()
        ));
    }
}
