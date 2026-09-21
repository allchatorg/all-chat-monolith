-- Apply before deploying standalone sticker message support to an existing
-- PostgreSQL database. Old messages remain unchanged with a null sticker_id.
BEGIN;

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS sticker_id varchar(32);

COMMIT;
