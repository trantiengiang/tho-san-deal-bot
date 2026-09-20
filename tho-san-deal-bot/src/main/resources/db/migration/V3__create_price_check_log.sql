-- V3: Create price_check_log table

CREATE TABLE price_check_log (
    id            BIGSERIAL PRIMARY KEY,
    watch_item_id BIGINT         NOT NULL,
    checked_price NUMERIC(19, 2),
    status        VARCHAR(20)    NOT NULL,
    message       VARCHAR(1000),
    checked_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_price_check_log_watch_item
        FOREIGN KEY (watch_item_id) REFERENCES watch_item(id),

    CONSTRAINT chk_price_check_status
        CHECK (status IN ('SUCCESS', 'FAILED', 'NOT_AVAILABLE'))
);

COMMENT ON TABLE price_check_log IS 'Log of all price check attempts by the scheduler';
