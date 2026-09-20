-- V8: Create lazada_session table for secure encrypted session management
-- Phase 2: Stores AES-256-GCM encrypted versioned session cookies

CREATE TABLE lazada_session (
    id                          BIGSERIAL PRIMARY KEY,
    account_id                  VARCHAR(100),
    encrypted_payload           TEXT            NOT NULL,
    status                      VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    last_validated_at           TIMESTAMPTZ,
    last_successful_preview_at  TIMESTAMPTZ,
    last_error_summary          VARCHAR(1000),
    admin_notified_at           TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_lazada_session_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'INVALID', 'NEEDS_LOGIN'))
);

CREATE INDEX idx_lazada_session_status ON lazada_session(status);

COMMENT ON TABLE lazada_session IS 'Encrypted Lazada account sessions for authenticated price checking';
COMMENT ON COLUMN lazada_session.encrypted_payload IS 'AES-256-GCM versioned encrypted payload containing minimal cookies';
