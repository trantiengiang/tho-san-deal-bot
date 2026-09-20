-- V4: Create telegram_update_inbox table
-- MandatoryFix #1, #2: Durable idempotent inbox for Telegram webhook updates
-- update_id is the PRIMARY KEY — DB-level deduplication via INSERT ON CONFLICT DO NOTHING

CREATE TABLE telegram_update_inbox (
    update_id     BIGINT       PRIMARY KEY,   -- Telegram's own update_id IS the PK
    payload       TEXT,                        -- Serialized TelegramUpdate JSON
    status        VARCHAR(20)  NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMPTZ,
    error_message TEXT,
    retry_count   INT          NOT NULL DEFAULT 0,

    CONSTRAINT chk_inbox_status
        CHECK (status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED'))
);

COMMENT ON TABLE telegram_update_inbox IS
    'Durable inbox for Telegram updates. update_id PK ensures idempotency (deduplication via INSERT ON CONFLICT DO NOTHING).';
COMMENT ON COLUMN telegram_update_inbox.update_id IS
    'Telegram''s own update_id. PRIMARY KEY guarantees exactly-once processing even on retries.';
