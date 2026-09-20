-- V1: Create telegram_user table
-- MandatoryFix #5: UNIQUE constraint on telegram_user_id for atomic upsert

CREATE TABLE telegram_user (
    id               BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT       NOT NULL,
    username         VARCHAR(255),
    first_name       VARCHAR(255),
    last_name        VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_telegram_user_id UNIQUE (telegram_user_id)
);

COMMENT ON TABLE telegram_user IS 'Telegram users who have interacted with the bot';
COMMENT ON COLUMN telegram_user.telegram_user_id IS 'Telegram''s own numeric user ID';
