package com.emanstagram.user.dto;

import com.emanstagram.user.ThemePreference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Payloads for profiles, profile editing and user lists. */
public final class UserDtos {

    private UserDtos() {
    }

    /**
     * The compact shape embedded in posts, comments, messages and lists.
     *
     * <p>{@code following} is only filled in where the UI shows a follow
     * button next to the person, and is omitted (null) everywhere else.
     */
    public record UserSummary(
            UUID id,
            String username,
            String displayName,
            String effectiveName,
            String avatarUrl,
            boolean verified,
            Boolean following
    ) {
        public UserSummary withFollowing(boolean value) {
            return new UserSummary(id, username, displayName, effectiveName, avatarUrl, verified, value);
        }
    }

    /** A profile page as seen by a particular viewer. */
    public record ProfileResponse(
            UUID id,
            String username,
            String displayName,
            String effectiveName,
            String bio,
            String pronouns,
            String website,
            String location,
            String avatarUrl,
            String bannerUrl,
            boolean verified,
            int followerCount,
            int followingCount,
            int postCount,
            Instant createdAt,
            boolean me,
            boolean following,
            boolean followsYou,
            boolean blockedByMe,
            boolean hasActiveStory
    ) {
    }

    /**
     * Partial update: a null field is left unchanged, and an empty string
     * clears an optional field.
     */
    public record UpdateProfileRequest(
            @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_.]+$",
                    message = "Username can only contain letters, numbers, dots and underscores")
            String username,

            @Size(max = 80, message = "Display name is limited to 80 characters")
            String displayName,

            @Size(max = 500, message = "Bio is limited to 500 characters")
            String bio,

            @Size(max = 40, message = "Pronouns are limited to 40 characters")
            String pronouns,

            @Size(max = 255, message = "Website is limited to 255 characters")
            String website,

            @Size(max = 120, message = "Location is limited to 120 characters")
            String location,

            ThemePreference theme,

            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Accent must be a hex colour like #6366f1")
            String accentColor
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Enter your current password")
            String currentPassword,

            @NotBlank(message = "Enter a new password")
            @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
            String newPassword
    ) {
    }

    public record UsernameAvailability(boolean available, String message) {
    }
}
