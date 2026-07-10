-- V003 — Create cognitive_sessions table
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE cognitive_sessions;

CREATE TABLE IF NOT EXISTS cognitive_sessions (
    session_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          TEXT         NOT NULL,
    tenant_id        TEXT         NOT NULL,
    turn_summaries   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    emotional_state  TEXT         NOT NULL DEFAULT 'NEUTRAL',
    engagement_score DOUBLE PRECISION NOT NULL DEFAULT 0.5
                                  CHECK (engagement_score BETWEEN 0 AND 1),
    status           TEXT         NOT NULL DEFAULT 'ACTIVE'
                                  CHECK (status IN ('ACTIVE', 'CLOSED')),
    started_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_active_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cognitive_sessions_user
    ON cognitive_sessions (tenant_id, user_id, last_active_at DESC);

-- At most one ACTIVE session per user per tenant
CREATE UNIQUE INDEX IF NOT EXISTS idx_cognitive_sessions_one_active
    ON cognitive_sessions (tenant_id, user_id)
    WHERE status = 'ACTIVE';
