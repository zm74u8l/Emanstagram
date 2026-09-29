package com.emanstagram.storage;

import com.emanstagram.common.ApiException;
import com.emanstagram.config.EmanstagramProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Gate #2 of 3 for uploads (the client checks first, Spring's multipart
 * limit is the outer net). Every rejection here is a specific, actionable
 * message rather than a generic failure.
 *
 * <p>The type of a file is decided by <b>reading its first bytes</b>, never by
 * the Content-Type the client sent. A client can label anything
 * {@code image/png}; trusting that label would let people use the media
 * bucket as free hosting for arbitrary files. Each validate method returns
 * the detected MIME type, and that is what gets stored and served.
 */
@Service
public class MediaValidationService {

    private static final int SNIFF_BYTES = 16;

    private final EmanstagramProperties.Media limits;

    public MediaValidationService(EmanstagramProperties properties) {
        this.limits = properties.media();
    }

    /** Validates a post image (cap 10 MB) and returns its real MIME type. */
    public String validateImage(MultipartFile file) {
        requirePresent(file, "image");
        if (file.getSize() > limits.maxImageBytes()) {
            throw tooLarge("That image is too large. The limit is " + mb(limits.maxImageBytes()) + " MB.");
        }
        return requireKind(file, true, false, "Images must be JPEG, PNG, WebP or GIF.");
    }

    /**
     * Validates a post video (cap 15 MB) and returns its real MIME type.
     */
    public String validateVideo(MultipartFile file) {
        requirePresent(file, "video");
        if (file.getSize() > limits.maxVideoBytes()) {
            throw tooLarge("That video is too large. The limit is " + mb(limits.maxVideoBytes()) + " MB. "
                    + "Tip: shorter clips upload faster.");
        }
        return requireKind(file, false, true, "Videos must be MP4, WebM or MOV.");
    }

    /** Validates an avatar (cap 5 MB). */
    public String validateAvatar(MultipartFile file) {
        requirePresent(file, "image");
        if (file.getSize() > limits.maxAvatarBytes()) {
            throw tooLarge("That profile picture is too large. The limit is " + mb(limits.maxAvatarBytes()) + " MB.");
        }
        return requireKind(file, true, false, "Profile pictures must be JPEG, PNG, WebP or GIF.");
    }

    /** Validates a story video (cap 20 MB). */
    public String validateStoryVideo(MultipartFile file) {
        requirePresent(file, "video");
        if (file.getSize() > limits.maxStoryVideoBytes()) {
            throw tooLarge("That story video is too large. The limit is " + mb(limits.maxStoryVideoBytes()) + " MB.");
        }
        return requireKind(file, false, true, "Story videos must be MP4, WebM or MOV.");
    }

    /** Chat attachments reuse the post caps so behaviour stays predictable. */
    public String validateChatAttachment(MultipartFile file) {
        requirePresent(file, "file");
        long cap = Math.max(limits.maxImageBytes(), limits.maxVideoBytes());
        if (file.getSize() > cap) {
            throw tooLarge("That attachment is too large. The limit is " + mb(cap) + " MB.");
        }
        return requireKind(file, true, true,
                "Attachments must be an image (JPEG, PNG, WebP, GIF) or a video (MP4, WebM, MOV).");
    }

    /** Validates an image or a video, whichever the bytes say it is. */
    public String validateImageOrVideo(MultipartFile file) {
        String sniffed = detect(file);
        return sniffed != null && isVideo(sniffed) ? validateVideo(file) : validateImage(file);
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

    /** Filesystem-safe extension derived from a validated MIME type. */
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

    // ---------------- content sniffing ----------------

    /**
     * The file's real type from its signature ("magic bytes"), or null when
     * it isn't a format this app accepts.
     */
    public static String detect(MultipartFile file) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(SNIFF_BYTES);
        } catch (IOException ex) {
            throw ApiException.badRequest("UPLOAD_UNREADABLE", "That file could not be read. Please try again.");
        }
        return detect(head);
    }

    static String detect(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (ascii(b, 0, "GIF87a") || ascii(b, 0, "GIF89a")) {
            return "image/gif";
        }
        if (ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")) {
            return "image/webp";
        }
        if (startsWith(b, 0, 0x1A, 0x45, 0xDF, 0xA3)) {
            return "video/webm";
        }
        // ISO base media (MP4/MOV): a box size, then "ftyp", then the brand.
        if (ascii(b, 4, "ftyp")) {
            if (ascii(b, 8, "qt  ")) {
                return "video/quicktime";
            }
            // HEIC/AVIF photos use the same container; they are images this app can't serve.
            if (ascii(b, 8, "heic") || ascii(b, 8, "heix") || ascii(b, 8, "mif1") || ascii(b, 8, "avif")) {
                return null;
            }
            return "video/mp4";
        }
        return null;
    }

    private String requireKind(MultipartFile file, boolean images, boolean videos, String message) {
        String type = detect(file);
        boolean ok = type != null
                && ((images && limits.imageTypeAllowed(type)) || (videos && limits.videoTypeAllowed(type)));
        if (!ok) {
            throw unsupported(message);
        }
        return type;
    }

    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] b, int offset, String text) {
        byte[] t = text.getBytes(StandardCharsets.US_ASCII);
        return b.length >= offset + t.length && Arrays.equals(b, offset, offset + t.length, t, 0, t.length);
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
