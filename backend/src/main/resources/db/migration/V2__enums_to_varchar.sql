-- =====================================================================
-- Emanstagram :: native ENUM types -> VARCHAR + CHECK
--
-- Same problem as citext in V1. Hibernate 6.6 has no JavaType for a
-- Postgres ENUM, so `ddl-auto: validate` refuses to start once an entity
-- maps one of these columns, and the H2 `local` profile cannot create
-- them at all. VARCHAR + CHECK keeps the same set of allowed values
-- and maps with a plain @Enumerated(STRING), the same as users.role.
--
-- Objects whose definitions embed an enum literal (the partial feed index
-- and two CHECK constraints) have to be dropped before the type change
-- and recreated after it.
-- =====================================================================

-- ---------------------------------------------------------------------
-- posts.kind, posts.visibility
-- ---------------------------------------------------------------------
DROP INDEX IF EXISTS ix_posts_public_feed;

ALTER TABLE posts
    ALTER COLUMN kind DROP DEFAULT,
    ALTER COLUMN visibility DROP DEFAULT;

ALTER TABLE posts
    ALTER COLUMN kind TYPE VARCHAR(20) USING kind::text,
    ALTER COLUMN visibility TYPE VARCHAR(20) USING visibility::text;

ALTER TABLE posts
    ALTER COLUMN kind SET DEFAULT 'IMAGE',
    ALTER COLUMN visibility SET DEFAULT 'PUBLIC',
    ADD CONSTRAINT ck_posts_kind CHECK (kind IN ('IMAGE', 'VIDEO', 'CAROUSEL')),
    ADD CONSTRAINT ck_posts_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS', 'PRIVATE'));

CREATE INDEX ix_posts_public_feed ON posts (created_at DESC) WHERE visibility = 'PUBLIC';

-- ---------------------------------------------------------------------
-- conversations.kind
-- ---------------------------------------------------------------------
ALTER TABLE conversations ALTER COLUMN kind DROP DEFAULT;
ALTER TABLE conversations ALTER COLUMN kind TYPE VARCHAR(20) USING kind::text;
ALTER TABLE conversations
    ALTER COLUMN kind SET DEFAULT 'DIRECT',
    ADD CONSTRAINT ck_conversations_kind CHECK (kind IN ('DIRECT', 'GROUP'));

-- ---------------------------------------------------------------------
-- messages.kind
-- ---------------------------------------------------------------------
ALTER TABLE messages DROP CONSTRAINT IF EXISTS ck_message_content;
ALTER TABLE messages ALTER COLUMN kind DROP DEFAULT;
ALTER TABLE messages ALTER COLUMN kind TYPE VARCHAR(20) USING kind::text;
ALTER TABLE messages
    ALTER COLUMN kind SET DEFAULT 'TEXT',
    ADD CONSTRAINT ck_messages_kind CHECK (kind IN ('TEXT', 'IMAGE', 'VIDEO', 'FILE', 'SYSTEM')),
    ADD CONSTRAINT ck_message_content CHECK (
        body IS NOT NULL OR storage_key IS NOT NULL OR kind = 'SYSTEM'
    );

-- ---------------------------------------------------------------------
-- notifications.kind
-- ---------------------------------------------------------------------
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notif_has_actor;
ALTER TABLE notifications ALTER COLUMN kind TYPE VARCHAR(20) USING kind::text;
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_kind CHECK (
        kind IN ('LIKE', 'COMMENT', 'FOLLOW', 'MENTION', 'MESSAGE', 'SYSTEM')
    ),
    ADD CONSTRAINT ck_notif_has_actor CHECK (actor_id IS NOT NULL OR kind = 'SYSTEM');

-- ---------------------------------------------------------------------
-- reports.reason
-- ---------------------------------------------------------------------
ALTER TABLE reports ALTER COLUMN reason TYPE VARCHAR(20) USING reason::text;
ALTER TABLE reports
    ADD CONSTRAINT ck_reports_reason CHECK (
        reason IN ('SPAM', 'HARASSMENT', 'NUDITY', 'VIOLENCE', 'OTHER')
    );

DROP TYPE post_visibility;
DROP TYPE post_kind;
DROP TYPE conversation_kind;
DROP TYPE message_kind;
DROP TYPE notification_kind;
DROP TYPE report_reason;

-- ---------------------------------------------------------------------
-- Indexes for access paths the V1 schema did not anticipate
-- ---------------------------------------------------------------------
-- "Saved" tab, newest first
CREATE INDEX ix_saved_posts_user_recent ON saved_posts (user_id, created_at DESC);
-- Story tray: active stories by author
CREATE INDEX ix_stories_author_active ON stories (author_id, expires_at);
-- "Who has blocked me" lookups (the PK only covers blocker -> blocked)
CREATE INDEX ix_blocks_blocked ON blocks (blocked_id);
-- Followers/following lists ordered by recency
CREATE INDEX ix_follows_follower_recent ON follows (follower_id, created_at DESC);
-- Replies under a comment, oldest first
CREATE INDEX ix_comments_parent_created ON comments (parent_id, created_at);
-- Open reports per target, for de-duplication
CREATE INDEX ix_reports_post ON reports (post_id) WHERE post_id IS NOT NULL;
CREATE INDEX ix_reports_comment ON reports (comment_id) WHERE comment_id IS NOT NULL;
