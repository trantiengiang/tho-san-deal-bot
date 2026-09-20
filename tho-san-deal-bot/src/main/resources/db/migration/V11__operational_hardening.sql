-- V11: Operational hardening before 24/7 live mode
-- 1. Update lazada_session status constraint to include CHALLENGED and PROBING
-- 2. Add challenge tracking, cooldown, and generation columns to lazada_session
-- 3. Add last_exact_price and last_exact_checked_at to watch_sku
-- 4. Allow null watch_item_id in notification_outbox for system/admin alerts

ALTER TABLE lazada_session DROP CONSTRAINT IF EXISTS chk_lazada_session_status;
ALTER TABLE lazada_session ADD CONSTRAINT chk_lazada_session_status
    CHECK (status IN ('ACTIVE', 'EXPIRED', 'INVALID', 'NEEDS_LOGIN', 'CHALLENGED', 'PROBING'));

ALTER TABLE lazada_session ADD COLUMN IF NOT EXISTS challenge_detected_at TIMESTAMPTZ;
ALTER TABLE lazada_session ADD COLUMN IF NOT EXISTS cooldown_until TIMESTAMPTZ;
ALTER TABLE lazada_session ADD COLUMN IF NOT EXISTS challenge_generation BIGINT NOT NULL DEFAULT 0;

ALTER TABLE watch_sku ADD COLUMN IF NOT EXISTS last_exact_price NUMERIC(19, 2);
ALTER TABLE watch_sku ADD COLUMN IF NOT EXISTS last_exact_checked_at TIMESTAMPTZ;

ALTER TABLE notification_outbox ALTER COLUMN watch_item_id DROP NOT NULL;
