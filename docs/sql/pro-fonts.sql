-- Apply before deploying the backend with schema validation. Existing users
-- keep the default fonts; no changes to messages or historical content are needed.
BEGIN;

ALTER TABLE chat_user
    ADD COLUMN IF NOT EXISTS username_font varchar(16) NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN IF NOT EXISTS message_font varchar(16) NOT NULL DEFAULT 'DEFAULT',
    ADD COLUMN IF NOT EXISTS font_revision bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS font_changes_date date,
    ADD COLUMN IF NOT EXISTS font_changes_count integer NOT NULL DEFAULT 0;

COMMIT;
