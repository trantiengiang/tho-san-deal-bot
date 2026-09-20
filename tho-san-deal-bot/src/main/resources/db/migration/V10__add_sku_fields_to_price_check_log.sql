-- V10: Add per-SKU fields to price_check_log
-- Phase 2: Logs detailed SKU-level price checks and quality

ALTER TABLE price_check_log
    ADD COLUMN watch_sku_id BIGINT,
    ADD COLUMN sku_id VARCHAR(100),
    ADD COLUMN variant_name VARCHAR(1000),
    ADD COLUMN price_quality VARCHAR(30);

ALTER TABLE price_check_log
    ADD CONSTRAINT fk_price_check_log_watch_sku
        FOREIGN KEY (watch_sku_id) REFERENCES watch_sku(id) ON DELETE SET NULL;

CREATE INDEX idx_price_check_log_sku_id ON price_check_log(sku_id);
CREATE INDEX idx_price_check_log_watch_sku ON price_check_log(watch_sku_id);

COMMENT ON COLUMN price_check_log.sku_id IS 'SKU ID checked during this cycle';
COMMENT ON COLUMN price_check_log.price_quality IS 'Quality of price established: EXACT_ACCOUNT, SALE_PRICE_ONLY, UNKNOWN';
