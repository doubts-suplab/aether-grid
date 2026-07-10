# Session Log — Aether Core

## 2026-06-15 — Phase 0: Scaffold

**What was done:**
- Created independent Maven multi-module project (`aether-core-parent`) with 4 modules: `core-domain`, `core-memory`, `core-api`, `core-infra`
- Implemented domain layer: `PersonalMemory`, `MemoryType`, `CognitiveSession`, `PersonalContext` records; `PersonalMemoryStore` and `PersonalContextProvider` port interfaces
- Implemented `PGVectorPersonalMemoryStore` adapter (pgvector cosine similarity, explicit column lists, `NamedParameterJdbcTemplate`)
- Implemented `PersonalEmbeddingService` (Ollama `all-minilm`, 384-dim, graceful fallback on error)
- Created `AetherCoreApplication` Spring Boot app (port 8082, `scanBasePackages = "com.suplab.aether.core"`)
- Implemented `PersonalContextController` (`GET /api/v1/personal-context/{tenantId}/{userId}`) — the endpoint Aether Grid calls
- Implemented `PersonalMemoryController` (`POST`, `GET /count`, `DELETE`)
- Flyway migrations: V001 (personal_memories table), V002 (pgvector extension + vector(384) column + ivfflat index)
- Docker Compose for local dev (postgres-core on 5433, aether-core on 8082)
- GitHub Actions CI + quality-gate workflows
- CLAUDE.md, README.md, `.claude/memory/` (7 files), `.claude/agents/` (19 agents), `docs/` (index.html, architecture.md, roadmap.md, progress.md)

**Status:** Phase 0 complete. Ready to extract to `suplab/aether-core` repo.

**Next session:** Phase 1 — Personal Memory Engine
- Implement reinforce-on-read in `PGVectorPersonalMemoryStore`
- Add Testcontainers integration test for memory store
- Add `PersonalContextProvider` implementation in `core-memory`
- Wire `@ConditionalOnProperty` for embedding (skip if Ollama unavailable)

---

## Session: 2026-07-10 — Phase 1 + Phase 2 complete

**Phase 1 (Personal Memory Engine):**
- Reinforce-on-read in PGVectorPersonalMemoryStore; DefaultPersonalContextProvider; controller rewired to port; @ConditionalOnProperty embedding; JaCoCo 80% gate; 18 unit tests + PGVectorPersonalMemoryStoreIT

**Phase 2 (Cognitive Session Management):**
- SessionStatus enum; CognitiveSession.start()/withTurn()/close(); CognitiveSessionStore + UserPreferenceStore ports
- V003 cognitive_sessions (partial unique ACTIVE index) + V004 user_preferences migrations
- JdbcCognitiveSessionStore, JdbcUserPreferenceStore adapters; context provider enriched (session overrides memory-derived state)
- CognitiveSessionController (create/list/get/addTurn/close), UserPreferenceController (GET/PUT)
- 31 unit tests green; 2 new Testcontainers ITs (run in CI)

**Next session:** Phase 3 — GDPR + Right to Erasure
- DELETE /api/v1/users/{userId}/memories (erase all) and DELETE /api/v1/users/{userId} (full erasure)
- Memory export endpoint, data_retention_days, erasure audit log, V005 user_privacy_settings
