-- V005 — Create personal_memories_archive table
-- Faded memories (strength below the archive threshold) are moved here, not deleted:
-- forgetting is graceful, and archived memories keep their embeddings for potential restore.
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE personal_memories_archive;

CREATE TABLE IF NOT EXISTS personal_memories_archive (
    id               UUID         PRIMARY KEY,
    user_id          TEXT         NOT NULL,
    memory_type      TEXT         NOT NULL
                                  CHECK (memory_type IN ('EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'EMOTIONAL')),
    content          TEXT         NOT NULL,
    embedding        vector(384),
    strength         DOUBLE PRECISION NOT NULL,
    access_count     INTEGER      NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    last_accessed_at TIMESTAMPTZ  NOT NULL,
    archived_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_personal_memories_archive_user
    ON personal_memories_archive (user_id, archived_at DESC);
