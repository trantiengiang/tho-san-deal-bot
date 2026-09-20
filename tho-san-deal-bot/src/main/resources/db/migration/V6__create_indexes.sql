-- V6: Create performance indexes
-- MandatoryFix #24: All indexes explicit in migration

-- telegram_user
-- (uq_telegram_user_id already created as UNIQUE constraint in V1)

-- watch_item
CREATE INDEX idx_watch_item_user_id
    ON watch_item (user_id);

CREATE INDEX idx_watch_item_active
    ON watch_item (active)
    WHERE active = TRUE;

CREATE INDEX idx_watch_item_product_id
    ON watch_item (product_id)
    WHERE product_id IS NOT NULL;

-- price_check_log
CREATE INDEX idx_price_check_log_watch_item_id
    ON price_check_log (watch_item_id);

CREATE INDEX idx_price_check_log_checked_at
    ON price_check_log (checked_at DESC);

-- telegram_update_inbox
CREATE INDEX idx_telegram_update_inbox_status
    ON telegram_update_inbox (status)
    WHERE status IN ('RECEIVED', 'PROCESSING');

CREATE INDEX idx_telegram_update_inbox_received_at
    ON telegram_update_inbox (received_at DESC);

-- notification_outbox
CREATE INDEX idx_notification_outbox_status
    ON notification_outbox (status)
    WHERE status = 'PENDING';

CREATE INDEX idx_notification_outbox_created_at
    ON notification_outbox (created_at ASC);

COMMENT ON INDEX idx_watch_item_active IS 'Partial index for scheduler: only active items';
COMMENT ON INDEX idx_notification_outbox_status IS 'Partial index for outbox worker: only PENDING entries';
