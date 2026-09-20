-- V7: Create watch_sku table for per-SKU price tracking
-- Phase 2: Each WatchItem tracks multiple SKUs independently

CREATE TABLE watch_sku (
    id                  BIGSERIAL PRIMARY KEY,
    watch_item_id       BIGINT          NOT NULL,
    sku_id              VARCHAR(100)    NOT NULL,
    seller_sku          VARCHAR(255),
    variant_name        VARCHAR(1000),
    available           BOOLEAN         NOT NULL DEFAULT TRUE,
    stock               INT,
    original_price      NUMERIC(19, 2),
    sale_price          NUMERIC(19, 2),
    final_price         NUMERIC(19, 2),
    price_quality       VARCHAR(30)     NOT NULL DEFAULT 'UNKNOWN',
    last_checked_price  NUMERIC(19, 2),
    last_notified_price NUMERIC(19, 2),
    last_checked_at     TIMESTAMPTZ,
    last_notified_at    TIMESTAMPTZ,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_watch_sku_watch_item
        FOREIGN KEY (watch_item_id) REFERENCES watch_item(id) ON DELETE CASCADE,

    CONSTRAINT uq_watch_sku_item_sku
        UNIQUE (watch_item_id, sku_id),

    CONSTRAINT chk_watch_sku_price_quality
        CHECK (price_quality IN ('EXACT_ACCOUNT', 'SALE_PRICE_ONLY', 'UNKNOWN'))
);

CREATE INDEX idx_watch_sku_item_id ON watch_sku(watch_item_id);
CREATE INDEX idx_watch_sku_item_available ON watch_sku(watch_item_id, available);

COMMENT ON TABLE watch_sku IS 'Individual SKU variants tracked under a WatchItem';
COMMENT ON COLUMN watch_sku.final_price IS 'Account-specific product payable price before shipping. NUMERIC(19,2)';
COMMENT ON COLUMN watch_sku.price_quality IS 'EXACT_ACCOUNT, SALE_PRICE_ONLY, or UNKNOWN';
