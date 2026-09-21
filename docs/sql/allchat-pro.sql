-- Add the allchat Pro billing projection and badge preferences to an existing
-- PostgreSQL schema before starting the application with schema validation.
-- This migration does not initialize or reset other application tables.
BEGIN;

ALTER TABLE chat_user
    ADD COLUMN IF NOT EXISTS pro_paid_through timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS show_pro_badge boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS pro_badge_revision bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pro_badge_last_published_visible boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS pro_subscription (
    user_id bigint PRIMARY KEY,
    stripe_subscription_id varchar(255) UNIQUE,
    stripe_schedule_id varchar(255),
    billing_interval varchar(16),
    status varchar(32) NOT NULL DEFAULT 'NONE',
    paid_through timestamp(6) with time zone,
    current_period_end timestamp(6) with time zone,
    cancel_at_period_end boolean NOT NULL DEFAULT false,
    scheduled_interval varchar(16),
    scheduled_change_at timestamp(6) with time zone,
    checkout_session_id varchar(255) UNIQUE,
    checkout_attempt_id varchar(36),
    checkout_interval varchar(16),
    checkout_expires_at timestamp(6) with time zone,
    last_reconciled_at timestamp(6) with time zone
);

CREATE INDEX IF NOT EXISTS idx_pro_subscription_reconcile
    ON pro_subscription (last_reconciled_at);

CREATE TABLE IF NOT EXISTS processed_stripe_event (
    event_id varchar(255) PRIMARY KEY,
    processed_at timestamp(6) with time zone NOT NULL
);

COMMIT;
