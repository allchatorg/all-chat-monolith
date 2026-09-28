-- Expand message storage for allchat Pro before deploying the updated application.
-- Existing content is preserved. Keep these wider columns when rolling back code.
BEGIN;

ALTER TABLE messages
    ALTER COLUMN content TYPE varchar(10000),
    ALTER COLUMN content_plain TYPE varchar(2500);

ALTER TABLE message_edit_history
    ALTER COLUMN content TYPE varchar(10000);

COMMIT;
