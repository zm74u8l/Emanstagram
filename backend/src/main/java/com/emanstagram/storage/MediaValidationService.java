package com.emanstagram.storage;

import com.emanstagram.common.ApiException;
import com.emanstagram.config.EmanstagramProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Gate #2 of 3 for uploads (the client checks first, Spring's multipart
 * limit is the outer net). Every rejection here is a specific, actionable
 * message rather than a generic failure.
 */
@Service
public class MediaValidationService {

    private final EmanstagramProperties.Media limits;

    public MediaValidationService(EmanstagramProperties properties) {
        this.limits = properties.media();
    }

    /** Validate a post image (cap 10 MB). */
    public void validateImage(MultipartFile file) {
        requirePresent(file, "image");
        if (file.getSize() > limits.maxImageBytes()) {
            throw tooLarge("That image is too large. The limit is "
                    + mb(limits.maxImageBytes()) + " MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !limits.imageTypeAllowed(contentType)) {
            throw unsupported("Images must be JPEG, PNG, WebP or GIF.");
        }
    }

    /**
     * Validate a post video (cap 15 MB). Videos arrive already transcoded to
     * 720p by the browser, so this rejects only files the client failed to
     * compress.
     */
    public void validateVideo(MultipartFile file) {
        requirePresent(file, "video");
        if (file.getSize() > limits.maxVideoBytes()) {
            throw tooLarge("That video is too large. The limit is "
                    + mb(limits.maxVideoBytes()) + " MB. "
                    + "Tip: shorter clips upload faster.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !limits.videoTypeAllowed(contentType)) {
            throw unsupported("Videos must be MP4, WebM or MOV.");
        }
    }

    /** Validate an avatar (cap 5 MB). */
    public void validateAvatar(MultipartFile file) {
        requirePresent(file, "image");
        if (file.getSize() > limits.maxAvatarBytes()) {
            throw tooLarge("That profile picture is too large. The limit is "
                    + mb(limits.maxAvatarBytes()) + " MB.");
        }
        if (file.getContentType() == null || !limits.imageTypeAllowed(file.getContentType())) {
            throw unsupported("Profile pictures must be JPEG, PNG, WebP or GIF.");
        }
    }

    /** Validate a story video (cap 20 MB). */
    public void validateStoryVideo(MultipartFile file) {
        requirePresent(file, "video");
        if (file.getSize() > limits.maxStoryVideoBytes()) {
            throw tooLarge("That story video is too large. The limit is "
                    + mb(limits.maxStoryVideoBytes()) + " MB.");
        }
        if (file.getContentType() == null || !limits.videoTypeAllowed(file.getContentType())) {
            throw unsupported("Story videos must be MP4, WebM or MOV.");
        }
    }

    /** Chat attachments reuse the post caps so behaviour stays predictable. */
    public void validateChatAttachment(MultipartFile file) {
        requirePresent(file, "file");
        long cap = Math.max(limits.maxImageBytes(), limits.maxVideoBytes());
        if (file.getSize() > cap) {
            throw tooLarge("That attachment is too large. The limit is " + mb(cap) + " MB.");
        }
        String contentType = file.getContentType();
        boolean allowed = (contentType != null)
                && (limits.imageTypeAllowed(contentType) || limits.videoTypeAllowed(contentType));
        if (!allowed) {
            throw unsupported("Attachments must be an image (JPEG, PNG, WebP, GIF) "
                    + "or a video (MP4, WebM, MOV).");
        }
    }

    public void validateCaption(String caption) {
        if (caption != null && caption.length() > limits.maxCaptionLength()) {
            throw ApiException.badRequest("CAPTION_TOO_LONG",
                    "Captions are limited to " + limits.maxCaptionLength() + " characters.");
        }
    }

    public void validateCarouselCount(int count) {
        if (count > limits.maxCarouselItems()) {
            throw ApiException.badRequest("TOO_MANY_ITEMS",
                    "You can upload up to " + limits.maxCarouselItems() + " items per post.");
        }
    }

    /** Filesystem-safe extension derived from the validated MIME type. */
    public String extensionFor(String mimeType) {
        if (mimeType == null) {
            return "bin";
        }
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            case "video/quicktime" -> "mov";
            default -> "bin";
        };
    }

    /** True when the MIME type is a video, used to pick a post kind. */
    public boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.toLowerCase().startsWith("video/");
    }

    public EmanstagramProperties.Media limits() {
        return limits;
    }

    // ---------------- helpers ----------------

    private static void requirePresent(MultipartFile file, String label) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("FILE_MISSING", "Please choose a " + label + " to upload.");
        }
    }

    private static ApiException tooLarge(String message) {
        return ApiException.payloadTooLarge("FILE_TOO_LARGE", message);
    }

    private static ApiException unsupported(String message) {
        return ApiException.unsupportedMediaType("UNSUPPORTED_FILE_TYPE", message);
    }

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }
}
