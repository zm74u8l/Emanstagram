package com.emanstagram.user;

import jakarta.persistence.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A platform account.
 *
 * <p>Note there is no image blob column by design: {@code avatarKey} and
 * {@code bannerKey} hold Supabase Storage object keys, never bytes.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Plain varchar, paired with a case-insensitive UNIQUE index in the
     * schema.
     *
     * <p>The schema originally used Postgres {@code citext} here. That type
     * has no JavaType in Hibernate 6.6, so it reports as {@code Types#OTHER}
     * and {@code ddl-auto: validate} refuses to start with "found [citext
     * (Types#OTHER)], but expecting [varchar(255)]". Switching the column to
     * varchar keeps the same case-insensitive uniqueness guarantee via
     * {@code ux_users_username_lower}, and works on both Postgres and the H2
     * profile the tests use.
     */
    @Column(name = "username", nullable = false, length = 30)
    private String username;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "pronouns", length = 40)
    private String pronouns;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "location", length = 120)
    private String location;

    @Column(name = "avatar_key", length = 500)
    private String avatarKey;

    @Column(name = "banner_key", length = 500)
    private String bannerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 20)
    private ThemePreference theme = ThemePreference.SYSTEM;

    @Column(name = "accent_color", nullable = false, length = 9)
    private String accentColor = "#6366f1";

    @Column(name = "is_private", nullable = false)
    private boolean privateAccount = false;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "follower_count", nullable = false)
    private int followerCount = 0;

    @Column(name = "following_count", nullable = false)
    private int followingCount = 0;

    @Column(name = "post_count", nullable = false)
    private int postCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Required by JPA. Must be at least protected: Hibernate builds a runtime
     * proxy subclass, which cannot invoke a private constructor.
     */
    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Display name if set, otherwise fall back to the username. */
    public String effectiveName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : username;
    }

    /**
     * Identity is the database id. {@code Hibernate.getClass} is used so a lazy
     * proxy of this entity still compares equal to its real instance.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        UUID otherId = ((User) o).id;
        return id != null && id.equals(otherId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** True when this instance is an uninitialised Hibernate proxy. */
    public boolean isProxy() {
        return this instanceof HibernateProxy;
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPronouns() {
        return pronouns;
    }

    public void setPronouns(String pronouns) {
        this.pronouns = pronouns;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public String getBannerKey() {
        return bannerKey;
    }

    public void setBannerKey(String bannerKey) {
        this.bannerKey = bannerKey;
    }

    public ThemePreference getTheme() {
        return theme;
    }

    public void setTheme(ThemePreference theme) {
        this.theme = theme;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public boolean isPrivateAccount() {
        return privateAccount;
    }

    public void setPrivateAccount(boolean privateAccount) {
        this.privateAccount = privateAccount;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public int getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(int followerCount) {
        this.followerCount = followerCount;
    }

    public int getFollowingCount() {
        return followingCount;
    }

    public void setFollowingCount(int followingCount) {
        this.followingCount = followingCount;
    }

    public int getPostCount() {
        return postCount;
    }

    public void setPostCount(int postCount) {
        this.postCount = postCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
