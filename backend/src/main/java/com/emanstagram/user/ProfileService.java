package com.emanstagram.user;

import com.emanstagram.auth.dto.AuthDtos.UserResponse;
import com.emanstagram.common.AfterCommit;
import com.emanstagram.common.ApiException;
import com.emanstagram.common.Times;
import com.emanstagram.social.BlockRepository;
import com.emanstagram.social.FollowRepository;
import com.emanstagram.social.SocialService;
import com.emanstagram.storage.MediaValidationService;
import com.emanstagram.storage.SupabaseStorageService;
import com.emanstagram.story.StoryRepository;
import com.emanstagram.user.dto.UserDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Viewing profiles and editing your own. */
@Service
public class ProfileService {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_.]{3,30}$");

    private final UserRepository users;
    private final FollowRepository follows;
    private final BlockRepository blocks;
    private final StoryRepository stories;
    private final SupabaseStorageService storage;
    private final MediaValidationService validation;

    public ProfileService(UserRepository users, FollowRepository follows, BlockRepository blocks,
                          StoryRepository stories, SupabaseStorageService storage,
                          MediaValidationService validation) {
        this.users = users;
        this.follows = follows;
        this.blocks = blocks;
        this.stories = stories;
        this.storage = storage;
        this.validation = validation;
    }

    /**
     * A profile as {@code viewer} sees it. Someone who has blocked the viewer
     * reads as not existing, exactly like a deleted account.
     */
    @Transactional(readOnly = true)
    public ProfileResponse profile(String username, User viewer) {
        User user = users.findByUsernameIgnoreCase(username).orElseThrow(SocialService::userNotFound);
        boolean me = user.getId().equals(viewer.getId());
        if (!me && blocks.existsByBlockerIdAndBlockedId(user.getId(), viewer.getId())) {
            throw SocialService.userNotFound();
        }
        boolean blockedByMe = !me && blocks.existsByBlockerIdAndBlockedId(viewer.getId(), user.getId());
        return new ProfileResponse(
                user.getId(), user.getUsername(), user.getDisplayName(), user.effectiveName(),
                user.getBio(), user.getPronouns(), user.getWebsite(), user.getLocation(),
                storage.publicUrl(user.getAvatarKey()), storage.publicUrl(user.getBannerKey()),
                user.isVerified(), user.getFollowerCount(), user.getFollowingCount(), user.getPostCount(),
                user.getCreatedAt(), me,
                !me && follows.existsByFollowerIdAndFolloweeId(viewer.getId(), user.getId()),
                !me && follows.existsByFollowerIdAndFolloweeId(user.getId(), viewer.getId()),
                blockedByMe,
                !blockedByMe && stories.hasActive(user.getId(), Times.now()));
    }

    @Transactional
    public UserResponse update(User me, UpdateProfileRequest request) {
        User user = users.findById(me.getId()).orElseThrow(SocialService::userNotFound);

        if (request.username() != null && !request.username().equalsIgnoreCase(user.getUsername())) {
            String wanted = request.username().trim();
            if (users.existsByUsernameIgnoreCase(wanted)) {
                throw ApiException.conflict("USERNAME_TAKEN", "That username is already taken.");
            }
            user.setUsername(wanted);
        } else if (request.username() != null) {
            // Same name, different capitalisation: allowed, it's still theirs.
            user.setUsername(request.username().trim());
        }
        if (request.displayName() != null) {
            user.setDisplayName(blankToNull(request.displayName()));
        }
        if (request.bio() != null) {
            user.setBio(blankToNull(request.bio()));
        }
        if (request.pronouns() != null) {
            user.setPronouns(blankToNull(request.pronouns()));
        }
        if (request.location() != null) {
            user.setLocation(blankToNull(request.location()));
        }
        if (request.website() != null) {
            user.setWebsite(normaliseWebsite(request.website()));
        }
        if (request.theme() != null) {
            user.setTheme(request.theme());
        }
        if (request.accentColor() != null) {
            user.setAccentColor(request.accentColor().toLowerCase(Locale.ROOT));
        }
        return toResponse(user);
    }

    @Transactional
    public UserResponse setAvatar(User me, MultipartFile file) {
        validation.validateAvatar(file);
        return replaceImage(me, file, true);
    }

    @Transactional
    public UserResponse setBanner(User me, MultipartFile file) {
        validation.validateImage(file);
        return replaceImage(me, file, false);
    }

    @Transactional
    public UserResponse clearAvatar(User me) {
        User user = users.findById(me.getId()).orElseThrow(SocialService::userNotFound);
        String old = user.getAvatarKey();
        user.setAvatarKey(null);
        AfterCommit.run(() -> storage.delete(old));
        return toResponse(user);
    }

    @Transactional
    public UserResponse clearBanner(User me) {
        User user = users.findById(me.getId()).orElseThrow(SocialService::userNotFound);
        String old = user.getBannerKey();
        user.setBannerKey(null);
        AfterCommit.run(() -> storage.delete(old));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UsernameAvailability availability(String username, UUID currentUserId) {
        String wanted = username == null ? "" : username.trim();
        if (!USERNAME.matcher(wanted).matches()) {
            return new UsernameAvailability(false,
                    "3-30 characters: letters, numbers, dots and underscores.");
        }
        var existing = users.findByUsernameIgnoreCase(wanted);
        if (existing.isEmpty() || (currentUserId != null && existing.get().getId().equals(currentUserId))) {
            return new UsernameAvailability(true, null);
        }
        return new UsernameAvailability(false, "That username is taken.");
    }

    // ---------------- helpers ----------------

    /**
     * Uploads first, swaps the key, and deletes the old object only after the
     * commit, so a failure at any point leaves the previous picture intact.
     */
    private UserResponse replaceImage(User me, MultipartFile file, boolean avatar) {
        User user = users.findById(me.getId()).orElseThrow(SocialService::userNotFound);
        String key;
        try {
            key = storage.upload(SupabaseStorageService.AVATARS, user.getId(), file.getBytes(),
                    file.getContentType(), validation.extensionFor(file.getContentType()));
        } catch (IOException ex) {
            throw ApiException.badRequest("UPLOAD_UNREADABLE", "That file could not be read. Please try again.");
        }
        String old = avatar ? user.getAvatarKey() : user.getBannerKey();
        if (avatar) {
            user.setAvatarKey(key);
        } else {
            user.setBannerKey(key);
        }
        AfterCommit.run(() -> storage.delete(old));
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user, storage.publicUrl(user.getAvatarKey()),
                storage.publicUrl(user.getBannerKey()));
    }

    /**
     * Accepts "example.com" and stores "https://example.com". Anything that
     * isn't http(s) is rejected, which keeps javascript: URLs out of hrefs.
     */
    private static String normaliseWebsite(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        if (!value.matches("(?i)^https?://.*")) {
            value = "https://" + value;
        }
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || !uri.getHost().contains(".")) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_WEBSITE", "Enter a valid website, like example.com.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
