-- V5: Create notification_outbox table
-- MandatoryFix #9, #10: Decouples DB transactions from Telegram HTTP calls
-- trigger_fingerprint UNIQUE prevents duplicate notifications even on scheduler retry

CREATE TABLE notification_outbox (
    id                   BIGSERIAL PRIMARY KEY,
    watch_item_id        BIGINT        NOT NULL,
    telegram_chat_id     VARCHAR(100)  NOT NULL,
    message_text         TEXT          NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempt_count        INT           NOT NULL DEFAULT 0,
    last_error           TEXT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    sent_at              TIMESTAMPTZ,
    trigger_fingerprint  VARCHAR(64)   NOT NULL,

    CONSTRAINT fk_notification_outbox_watch_item
        FOREIGN KEY (watch_item_id) REFERENCES watch_item(id),

    -- Unique fingerprint prevents duplicate outbox entries for same price event
    -- fingerprint = SHA-256(watchItemId + ":" + finalPrice + ":" + triggerReason)
    CONSTRAINT uq_notification_outbox_fingerprint
        UNIQUE (trigger_fingerprint),

    CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'))
);

COMMENT ON TABLE notification_outbox IS
    'Outbox for Telegram channel notifications. Scheduler writes here; separate worker delivers.';
COMMENT ON COLUMN notification_outbox.trigger_fingerprint IS
    'SHA-256 of (watchItemId:finalPrice:triggerReason). UNIQUE prevents duplicate notification events.';
