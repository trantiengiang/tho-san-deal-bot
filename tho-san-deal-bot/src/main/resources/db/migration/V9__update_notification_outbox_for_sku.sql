-- V9: Update notification_outbox table for per-SKU alerts
-- Phase 2: Links outbox notifications to specific WatchSku

ALTER TABLE notification_outbox
    ADD COLUMN watch_sku_id BIGINT;

ALTER TABLE notification_outbox
    ADD CONSTRAINT fk_notification_outbox_watch_sku
        FOREIGN KEY (watch_sku_id) REFERENCES watch_sku(id) ON DELETE SET NULL;

CREATE INDEX idx_notification_outbox_watch_sku ON notification_outbox(watch_sku_id);

COMMENT ON COLUMN notification_outbox.watch_sku_id IS 'Specific SKU variant that triggered this notification (nullable for legacy/product alerts)';
