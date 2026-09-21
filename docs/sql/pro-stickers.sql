-- Apply after allchat-pro.sql and before deploying the sticker feature to a
-- PostgreSQL database running with spring.jpa.hibernate.ddl-auto=validate.
-- Existing messages remain unchanged; their sticker_id is NULL.
BEGIN;

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS sticker_id varchar(32);

ALTER TABLE message_edit_history
    ADD COLUMN IF NOT EXISTS sticker_id varchar(32);

COMMIT;
