package com.emanstagram.auth.dto;

import com.emanstagram.user.Role;
import com.emanstagram.user.ThemePreference;
import com.emanstagram.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request and response payloads for the auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    // ---------------- requests ----------------

    public record RegisterRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_.]+$",
                    message = "Username can only contain letters, numbers, dots and underscores")
            String username,

            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
            String password
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Username or email is required")
            String identifier,

            @NotBlank(message = "Password is required")
            String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {
    }

    // ---------------- responses ----------------

    /**
     * The public shape of a user. Deliberately omits email and passwordHash
     * so it is safe to embed in other responses.
     */
    public record UserResponse(
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
            ThemePreference theme,
            String accentColor,
            boolean privateAccount,
            boolean verified,
            Role role,
            int followerCount,
            int followingCount,
            int postCount,
            Instant createdAt
    ) {
        public static UserResponse from(User user, String avatarUrl, String bannerUrl) {
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.effectiveName(),
                    user.getBio(),
                    user.getPronouns(),
                    user.getWebsite(),
                    user.getLocation(),
                    avatarUrl,
                    bannerUrl,
                    user.getTheme(),
                    user.getAccentColor(),
                    user.isPrivateAccount(),
                    user.isVerified(),
                    user.getRole(),
                    user.getFollowerCount(),
                    user.getFollowingCount(),
                    user.getPostCount(),
                    user.getCreatedAt()
            );
        }
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserResponse user
    ) {
    }
}
