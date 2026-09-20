-- V2: Create watch_item table
-- MandatoryFix #6: Partial unique index, NUMERIC money, @Version column
-- MandatoryFix #25: DB constraints on target_price range

CREATE TABLE watch_item (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    original_url        VARCHAR(2048)   NOT NULL,
    normalized_url      VARCHAR(2048),
    product_id          VARCHAR(255),
    product_name        VARCHAR(1000),
    target_price        NUMERIC(19, 2)  NOT NULL,
    last_checked_price  NUMERIC(19, 2),
    last_notified_price NUMERIC(19, 2),
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_checked_at     TIMESTAMPTZ,
    last_notified_at    TIMESTAMPTZ,
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT fk_watch_item_user
        FOREIGN KEY (user_id) REFERENCES telegram_user(id),

    -- MandatoryFix #25: DB-level constraints for target_price range
    CONSTRAINT chk_target_price_positive
        CHECK (target_price > 0),
    CONSTRAINT chk_target_price_max
        CHECK (target_price <= 1000000000),
    CONSTRAINT chk_status_valid
        CHECK (status IN ('ACTIVE', 'PAUSED', 'TRIGGERED', 'ERROR'))
);

-- MandatoryFix #6: Partial unique index prevents duplicate active watch items
-- This is the ultimate guard against race conditions beyond application-level check
CREATE UNIQUE INDEX uq_watch_item_active_duplicate
    ON watch_item (user_id, normalized_url, target_price)
    WHERE active = TRUE;

COMMENT ON TABLE watch_item IS 'Products users want to track for price drops';
COMMENT ON COLUMN watch_item.version IS 'Optimistic lock version for concurrent update protection';
COMMENT ON COLUMN watch_item.target_price IS 'Desired price threshold in VND. NUMERIC(19,2) — never FLOAT.';
COMMENT ON INDEX uq_watch_item_active_duplicate IS 'Prevents duplicate active watch items for same user/url/price';
