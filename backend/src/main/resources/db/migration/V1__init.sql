-- =====================================================================
-- Emanstagram :: initial schema
--
-- Design rule: MEDIA BYTES NEVER TOUCH THIS DATABASE.
-- Images/videos live in Supabase Storage. This schema stores only
-- object keys (post_media.storage_key, users.avatar_key, etc).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- USERS
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        CITEXT       NOT NULL,
    email           CITEXT       NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,

    display_name    VARCHAR(80),
    bio             VARCHAR(500),
    pronouns        VARCHAR(40),
    website         VARCHAR(255),
    location        VARCHAR(120),

    -- storage keys only, never image bytes
    avatar_key      VARCHAR(500),
    banner_key      VARCHAR(500),

    -- per-user UI customisation
    theme           VARCHAR(20)  NOT NULL DEFAULT 'system',   -- system | light | dark
    accent_color    VARCHAR(9)   NOT NULL DEFAULT '#6366f1',

    is_private      BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',      -- USER | MODERATOR | ADMIN

    -- denormalised counters, maintained by service layer
    follower_count  INTEGER      NOT NULL DEFAULT 0,
    following_count INTEGER      NOT NULL DEFAULT 0,
    post_count      INTEGER      NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness for login identifiers
CREATE UNIQUE INDEX ux_users_username_lower ON users (lower(username::text));
CREATE UNIQUE INDEX ux_users_email_lower    ON users (lower(email::text));

-- ---------------------------------------------------------------------
-- REFRESH TOKENS (rotation + revocation on logout)
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);

-- ---------------------------------------------------------------------
-- FOLLOWS  (composite PK prevents duplicate edges)
-- ---------------------------------------------------------------------
CREATE TABLE follows (
    follower_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT ck_no_self_follow CHECK (follower_id <> followee_id)
);

CREATE INDEX ix_follows_followee ON follows (followee_id);


-- ---------------------------------------------------------------------
-- POSTS
-- ---------------------------------------------------------------------
CREATE TYPE post_visibility AS ENUM ('PUBLIC', 'FOLLOWERS', 'PRIVATE');
CREATE TYPE post_kind      AS ENUM ('IMAGE', 'VIDEO', 'CAROUSEL');

CREATE TABLE posts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    caption     VARCHAR(2200),
    kind        post_kind NOT NULL DEFAULT 'IMAGE',
    visibility  post_visibility NOT NULL DEFAULT 'PUBLIC',
    location    VARCHAR(160),
    like_count  INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at   TIMESTAMPTZ
);

-- primary feed: public posts by recency
CREATE INDEX ix_posts_public_feed ON posts (created_at DESC) WHERE visibility = 'PUBLIC';
CREATE INDEX ix_posts_author      ON posts (author_id, created_at DESC);

-- ---------------------------------------------------------------------
-- POST MEDIA  (one row per carousel slot; holds the storage key only)
-- ---------------------------------------------------------------------
CREATE TABLE post_media (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id      UUID NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    position     INTEGER NOT NULL,
    storage_key  VARCHAR(500) NOT NULL,   -- e.g. posts/<uuid>/abc.webp
    mime_type    VARCHAR(100) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    width        INTEGER,
    height       INTEGER,
    duration_ms  INTEGER,                -- videos only
    blurhash     VARCHAR(200),            -- instant placeholder while loading
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_post_media_position UNIQUE (post_id, position),
    CONSTRAINT ck_media_position CHECK (position >= 0)
);

CREATE INDEX ix_post_media_post ON post_media (post_id, position);

-- ---------------------------------------------------------------------
-- LIKES
-- ---------------------------------------------------------------------
CREATE TABLE post_likes (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id     UUID NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX ix_post_likes_post ON post_likes (post_id);

-- ---------------------------------------------------------------------
-- COMMENTS  (self-referencing parent_id enables threading)
-- ---------------------------------------------------------------------
CREATE TABLE comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),

-- ---------------------------------------------------------------------
-- STORIES (ephemeral, 24h)
-- ---------------------------------------------------------------------
CREATE TABLE stories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    storage_key     VARCHAR(500) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    caption         VARCHAR(300),
    background_hex  VARCHAR(9),
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_stories_active ON stories (expires_at);

CREATE TABLE story_views (
    story_id    UUID NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    viewed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (story_id, user_id)
);

-- ---------------------------------------------------------------------
-- CHAT
-- ---------------------------------------------------------------------
CREATE TYPE conversation_kind AS ENUM ('DIRECT', 'GROUP');
CREATE TYPE message_kind    AS ENUM ('TEXT', 'IMAGE', 'VIDEO', 'FILE', 'SYSTEM');

CREATE TABLE conversations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind             conversation_kind NOT NULL DEFAULT 'DIRECT',
    title            VARCHAR(120),                 -- groups only
    avatar_key       VARCHAR(500),                 -- groups only
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    last_message_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_conversations_recent ON conversations (last_message_at DESC NULLS LAST);

CREATE TABLE conversation_members (
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER',  -- OWNER | ADMIN | MEMBER
    last_read_at    TIMESTAMPTZ,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX ix_conv_members_user ON conversation_members (user_id);

-- Guarantees a DIRECT conversation holds exactly 2 people and stops
-- duplicate DM rows being created for the same pair.
CREATE TABLE direct_conversation_keys (
    user_low         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_high        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    conversation_id  UUID NOT NULL UNIQUE REFERENCES conversations (id) ON DELETE CASCADE,
    PRIMARY KEY (user_low, user_high),
    CONSTRAINT ck_direct_pair CHECK (user_low < user_high)
);

CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    kind            message_kind NOT NULL DEFAULT 'TEXT',
    body            VARCHAR(4000),
    storage_key     VARCHAR(500),
    reply_to_id     UUID REFERENCES messages (id) ON DELETE SET NULL,
    edited_at       TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_message_content CHECK (
        body IS NOT NULL OR storage_key IS NOT NULL OR kind = 'SYSTEM'
    )
);

CREATE INDEX ix_messages_conversation ON messages (conversation_id, created_at DESC);

CREATE TABLE message_receipts (
    message_id   UUID NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    delivered_at TIMESTAMPTZ,
    read_at      TIMESTAMPTZ,
    PRIMARY KEY (message_id, user_id)
);

    post_id     UUID NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id   UUID REFERENCES comments (id) ON DELETE CASCADE,
    body        VARCHAR(1000) NOT NULL,
    like_count  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at   TIMESTAMPTZ
);

CREATE INDEX ix_comments_post ON comments (post_id, created_at DESC);
CREATE INDEX ix_comments_parent ON comments (parent_id);

CREATE TABLE comment_likes (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    comment_id  UUID NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, comment_id)
);

-- ---------------------------------------------------------------------
-- SAVED POSTS (private collections)
-- ---------------------------------------------------------------------
CREATE TABLE saved_posts (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id     UUID NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)

-- ---------------------------------------------------------------------
-- NOTIFICATIONS
-- ---------------------------------------------------------------------
CREATE TYPE notification_kind AS ENUM (
    'LIKE', 'COMMENT', 'FOLLOW', 'MENTION', 'MESSAGE', 'SYSTEM'
);

CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_id     UUID REFERENCES users (id) ON DELETE CASCADE,
    kind         notification_kind NOT NULL,
    post_id      UUID REFERENCES posts (id) ON DELETE CASCADE,
    comment_id   UUID REFERENCES comments (id) ON DELETE CASCADE,
    message      VARCHAR(300),
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notif_has_actor CHECK (actor_id IS NOT NULL OR kind = 'SYSTEM')
);

CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, created_at DESC);

-- ---------------------------------------------------------------------
-- SAFETY
-- ---------------------------------------------------------------------
CREATE TABLE blocks (
    blocker_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT ck_no_self_block CHECK (blocker_id <> blocked_id)
);

CREATE TYPE report_reason AS ENUM ('SPAM', 'HARASSMENT', 'NUDITY', 'VIOLENCE', 'OTHER');

CREATE TABLE reports (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    post_id        UUID REFERENCES posts (id) ON DELETE CASCADE,
    comment_id     UUID REFERENCES comments (id) ON DELETE CASCADE,
    reason         report_reason NOT NULL,
    details        VARCHAR(1000),
    resolved       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_report_has_target CHECK (
        target_user_id IS NOT NULL OR post_id IS NOT NULL OR comment_id IS NOT NULL
    )
);

CREATE INDEX ix_reports_unresolved ON reports (created_at DESC) WHERE resolved = FALSE;

);
