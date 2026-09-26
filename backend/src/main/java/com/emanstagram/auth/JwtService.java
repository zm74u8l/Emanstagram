package com.emanstagram.auth;

import com.emanstagram.config.EmanstagramProperties;
import com.emanstagram.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies the short-lived access tokens.
 *
 * <p>Refresh tokens are opaque random strings, not JWTs, and are tracked in
 * the database so they can be revoked. Only their SHA-256 hash is stored.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final String issuer;
    private final java.time.Duration accessTtl;
    private final java.time.Duration refreshTtl;

    public JwtService(EmanstagramProperties properties) {
        this.signingKey = buildKey(properties.jwt().secret());
        this.issuer = properties.jwt().issuer();
        this.accessTtl = properties.jwt().accessTtl();
        this.refreshTtl = properties.jwt().refreshTtl();
    }

    private static SecretKey buildKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes. Generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /** Signed access token carrying the user id, username and role. */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
    }

    /** Parses and verifies a token. Returns empty for anything invalid or expired. */
    public Optional<Claims> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"access".equals(claims.get("type", String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            // Expired or tampered tokens are routine, not exceptional.
            log.debug("Rejected access token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public Instant accessTokenExpiry() {
        return Instant.now().plus(accessTtl);
    }

    // --- refresh tokens: opaque + hashed, never JWT ---

    /**
     * 256 bits of entropy from the JDK CSPRNG, URL-safe. Opaque on purpose so
     * it carries no claims and cannot be decoded or forged.
     */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest; the raw token is never persisted. */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public long refreshTokenTtlSeconds() {
        return refreshTtl.toSeconds();
    }

    public java.time.Duration refreshTokenTtl() {
        return refreshTtl;
    }
}
