-- V004 — Create user_preferences table
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE user_preferences;

CREATE TABLE IF NOT EXISTS user_preferences (
    user_id     TEXT        PRIMARY KEY,
    preferences JSONB       NOT NULL DEFAULT '{}'::jsonb,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
