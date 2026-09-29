-- =====================================================================
-- Emanstagram :: abuse protection
--
-- 1. Byte sizes everywhere media is stored, so an account's storage use
--    can be totalled and capped. post_media already had size_bytes.
-- 2. Account suspension, so moderators can stop a person and not just
--    delete their posts one at a time.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN avatar_bytes     BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN banner_bytes     BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN suspended_at     TIMESTAMPTZ,
    ADD COLUMN suspended_reason VARCHAR(300);

ALTER TABLE stories  ADD COLUMN size_bytes       BIGINT NOT NULL DEFAULT 0;
ALTER TABLE messages ADD COLUMN attachment_bytes BIGINT NOT NULL DEFAULT 0;

-- Totalling one person's usage
CREATE INDEX ix_messages_sender_attachments ON messages (sender_id) WHERE storage_key IS NOT NULL;
-- The moderators' "suspended accounts" list
CREATE INDEX ix_users_suspended ON users (suspended_at) WHERE suspended_at IS NOT NULL;
