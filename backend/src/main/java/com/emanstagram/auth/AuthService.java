package com.emanstagram.auth;

import com.emanstagram.auth.dto.AuthDtos.*;
import com.emanstagram.common.ApiException;
import com.emanstagram.common.CurrentUser;
import com.emanstagram.storage.StorageService;
import com.emanstagram.user.RefreshToken;
import com.emanstagram.user.RefreshTokenRepository;
import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import com.emanstagram.user.dto.UserDtos.ChangePasswordRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StorageService storageService;
    private final CurrentUser currentUser;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       StorageService storageService,
                       CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.storageService = storageService;
        this.currentUser = currentUser;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Explicit pre-checks give a clean 409. The unique indexes remain the
        // real guarantee against a race between two simultaneous signups.
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN",
                    "That username is already taken. Try another one.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN",
                    "An account already exists with that email. Try signing in.");
        }

        User user = new User(username, email, passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        log.info("Registered new account: {}", username);
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();

        User user = identifier.contains("@")
                ? userRepository.findByEmailIgnoreCase(identifier).orElse(null)
                : userRepository.findByUsernameIgnoreCase(identifier).orElse(null);

        // Identical error for "no such user" and "wrong password" so this
        // endpoint cannot be used to enumerate which accounts exist.
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS",
                    "Incorrect username or password.");
        }

        return issueTokens(user);
    }

    /**
     * Rotates the refresh token: the presented one is revoked and a new one
     * is issued. If an already-revoked token is replayed, every session for
     * that user is killed, which is the standard reuse-detection response to
     * a stolen token.
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = jwtService.hashRefreshToken(request.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN",
                        "Your session has expired. Please sign in again."));

        if (stored.isRevoked()) {
            log.warn("Refresh token reuse detected for user {}. Revoking all sessions.",
                    stored.getUser().getId());
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId());
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED",
                    "Your session has expired. Please sign in again.");
        }

        if (!stored.isUsableAt(Instant.now())) {
            throw ApiException.unauthorized("REFRESH_TOKEN_EXPIRED",
                    "Your session has expired. Please sign in again.");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(stored.getUser());
    }

    /** Signs the user out of the current session only. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    /** Signs the account out of every device. */
    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    /**
     * Changes the password and revokes every refresh token, so a session on a
     * lost or shared device ends. The caller gets a new pair and stays signed in.
     */
    @Transactional
    public TokenResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "You need to sign in to do that."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Your current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeAllForUser(userId);
        log.info("Password changed for {}; other sessions revoked", user.getUsername());
        return issueTokens(userRepository.findById(userId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    /**
     * The signed-in account as a client-safe response, including resolved
     * media URLs. Used by /api/auth/me to rehydrate the session on boot.
     */
    @Transactional(readOnly = true)
    public UserResponse currentUserResponse() {
        return toUserResponse(currentUser.require());
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);

        String refreshValue = jwtService.generateRefreshTokenValue();
        Instant expiry = Instant.now().plus(jwtService.refreshTokenTtl());

        refreshTokenRepository.save(new RefreshToken(
                user,
                jwtService.hashRefreshToken(refreshValue),
                expiry
        ));

        return new TokenResponse(
                accessToken,
                refreshValue,
                "Bearer",
                jwtService.accessTokenTtlSeconds(),
                toUserResponse(user)
        );
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.from(
                user,
                storageService.publicUrl(user.getAvatarKey()),
                storageService.publicUrl(user.getBannerKey())
        );
    }
}
