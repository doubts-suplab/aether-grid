# Aether Core — Progress Tracker

> **Scope:** This tracker covers **Aether Core** (`suplab/aether-core`) only.
> For Aether Grid progress, see [suplab/aether-grid](https://github.com/suplab/aether-grid).

---

**Active Phase:** Phase 5 — Memory Decay + Reinforcement Scheduler
> Phases 4 and 5 prioritised ahead of Phase 3 (GDPR) by explicit decision.

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Personal Memory Engine | ✅ Complete | 2 |
| 2 | Cognitive Session Management | ✅ Complete | 2 |
| 3 | GDPR + Right to Erasure | ⏳ Planned (deferred) | — |
| 4 | Grid Feedback Loop (Kafka) | ✅ Complete | 3 |
| 5 | Memory Decay + Reinforcement Scheduler | 🔄 In Progress | 3 |
| 6 | Kubernetes + Helm | ⏳ Planned | — |

---

## Phase 0 — Scaffold ✅

**Commit:** `feat(core): scaffold Aether Core — personal cognitive engine sister project`

### What was done

**Maven project:**
- `pom.xml` — independent parent POM (`aether-core-parent`), Spring Boot 3.3.5 BOM, Java 21, `--enable-preview`, `-parameters` flags
- 4 modules: `core-domain`, `core-memory`, `core-api`, `core-infra`

**`core-domain` — pure domain (no Spring):**
- `PersonalMemory` record: id, userId, MemoryType, content, strength (0–1), accessCount, timestamps; `create()` factory; `reinforce()` returns new instance with strength+0.1
- `MemoryType` enum: EPISODIC, SEMANTIC, PROCEDURAL, EMOTIONAL
- `CognitiveSession` record: sessionId, userId, tenantId, turnSummaries, emotionalState, engagementScore, timestamps
- `PersonalContext` record: userId, tenantId, recentMemorySummaries, preferences, emotionalState, engagementScore, fetchedAt
- `PersonalMemoryStore` port interface: save, findSimilar, findByType, delete, countByUser
- `PersonalContextProvider` port interface: buildContext

**`core-memory` — pgvector adapter + embedding:**
- `PGVectorPersonalMemoryStore`: cosine similarity search (`<=> :query::vector`), explicit column lists, `NamedParameterJdbcTemplate`, `ON CONFLICT` upsert
- `PersonalEmbeddingService`: Ollama REST client (`/api/embeddings`), 384-dim, graceful fallback to zero vector on error

**`core-api` — Spring Boot application:**
- `AetherCoreApplication`: port 8082, `scanBasePackages = "com.suplab.aether.core"`
- `PersonalContextController`: `GET /api/v1/personal-context/{tenantId}/{userId}` — key endpoint consumed by Aether Grid
- `PersonalMemoryController`: `POST /api/v1/users/{userId}/memories`, `GET .../count`, `DELETE .../{memoryId}`
- `CoreApiConfig`: wires `PGVectorPersonalMemoryStore` and `PersonalEmbeddingService` as `@Bean`
- `application.yml`: port 8082, Flyway enabled, Ollama base-url configurable, actuator probes

**`core-infra` — infrastructure:**
- `V001__create_personal_memories.sql`: personal_memories table with indexes
- `V002__pgvector_personal_embeddings.sql`: pgvector extension, vector(384) column, ivfflat index
- `docker/docker-compose.yml`: postgres-core (port 5433) + aether-core (port 8082)

**`.claude/` setup:**
- 19 agent definitions
- 7 memory files seeded with Core context
- `CLAUDE.md` project brief

**Docs:**
- `README.md`, `docs/index.html`, `docs/architecture.md`, `docs/roadmap.md`, `docs/progress.md`
- GitHub Actions: `ci.yml`, `quality-gate.yml`

### Files created: 57

---

## Phase 1 — Personal Memory Engine ✅

**Commit:** `feat(core): Phase 1 — personal memory engine with reinforce-on-read and context provider`

### What was done

**Reinforce-on-read in `PGVectorPersonalMemoryStore`:**
- `findSimilar()` and `findByType()` now call `memory.reinforce()` on each returned result
- Reinforced state (strength +0.1 capped at 1.0, accessCount +1, lastAccessedAt = now) persisted immediately via UPDATE
- Extracted `mapRow()` helper to eliminate duplication
- `reinforceAndPersist()` private method handles the UPDATE without re-embedding

**`DefaultPersonalContextProvider` — new class in `core-memory`:**
- `com.suplab.aether.core.memory.context.DefaultPersonalContextProvider`
- Implements `PersonalContextProvider` port from `core-domain`
- Fetches EPISODIC + SEMANTIC memories for summaries, EMOTIONAL memories for state
- Returns `Optional.empty()` when user has zero memories across all types
- Engagement score = average of episodic memory strengths (default 0.5 when no episodic memories)

**`PersonalContextController` — refactored to use `PersonalContextProvider` port:**
- Removed direct `PersonalMemoryStore` and `PersonalEmbeddingService` dependencies from controller
- Now injects only `PersonalContextProvider` — single responsibility
- Falls back to `emptyContext()` (NEUTRAL, 0.5) when provider returns empty
- Always HTTP 200 — Grid callers always receive a usable response

**`CoreApiConfig` — updated:**
- `PersonalContextProvider` bean wired: `DefaultPersonalContextProvider(memoryStore, defaultMemoryLimit)`
- `@ConditionalOnProperty(name = "aether.core.embedding.enabled", havingValue = "true", matchIfMissing = true)` on embedding bean
- `aether.core.context.memory-limit` config property (default 5)

**`PersonalMemoryController` — optional embedding:**
- `Optional<PersonalEmbeddingService>` via constructor injection
- When embedding disabled (`aether.core.embedding.enabled=false`), stores zero-vector — other endpoints remain functional

**Unit tests — 18 tests, all green:**
- `PersonalMemoryTest` (12 tests): `create()`, `reinforce()`, validation, all MemoryType values
- `DefaultPersonalContextProviderTest` (6 tests): empty/non-empty contexts, emotional state derivation, engagement score calculation, user/tenant isolation

**Testcontainers integration test — `PGVectorPersonalMemoryStoreIT`:**
- `pgvector/pgvector:pg16` container
- Flyway migrations run in-test
- Tests: save+findByType round-trip, reinforce-on-read (strength progression), findSimilar returns reinforced, countByUser, cross-user isolation, upsert semantics
- Runs in CI (Docker unavailable in local scaffold env)

**JaCoCo 80% line coverage gate:**
- Added to parent `pom.xml` pluginManagement
- `prepare-agent` → `report` → `check` at `verify` phase
- `argLine` property defaulted to empty to prevent `@{argLine}` resolution failure

**`application.yml` additions:**
- `aether.core.embedding.enabled: ${EMBEDDING_ENABLED:true}`
- `aether.core.context.memory-limit: ${CONTEXT_MEMORY_LIMIT:5}`

### Files changed: 9 | Tests added: 18 + 9 IT scenarios

---

## Phase 2 — Cognitive Session Management ✅

**Commit:** `feat(core): Phase 2 — cognitive sessions, user preferences, session-enriched context`

### What was done

**Domain (`core-domain`):**
- `SessionStatus` enum: ACTIVE | CLOSED
- `CognitiveSession` extended with `status` field and behaviour:
  - `start(tenantId, userId)` factory — new ACTIVE session, no turns
  - `withTurn(summary, emotionalState, engagementScore)` — appends turn, updates state; throws `IllegalStateException` on closed sessions
  - `close()` — idempotent transition to CLOSED
- `CognitiveSessionStore` port: save, findById, findActive, findByUser
- `UserPreferenceStore` port: find, save (replace semantics)

**Migrations:**
- `V003__create_cognitive_sessions.sql` — JSONB turn_summaries, partial UNIQUE index enforcing one ACTIVE session per (tenant, user)
- `V004__create_user_preferences.sql` — one JSONB document per user

**Adapters (`core-memory`):**
- `JdbcCognitiveSessionStore` — JSONB serialisation via Jackson; saving an ACTIVE session closes the user's previous active session in the tenant
- `JdbcUserPreferenceStore` — JSONB upsert, replace-on-save
- `DefaultPersonalContextProvider` enriched: active session's emotionalState/engagementScore override memory-derived values; session turns prepended to summaries; preferences populated from store

**API (`core-api`):**
- `CognitiveSessionController` — POST create (closes prior active), GET list, GET by id, PATCH add turn (409 on closed session), POST close
- `UserPreferenceController` — GET / PUT `/api/v1/users/{userId}/preferences`
- `CoreApiConfig` — CognitiveSessionStore, UserPreferenceStore beans; context provider rewired with all three stores

**Tests — 31 unit tests green:**
- `CognitiveSessionTest` (9): start/withTurn/close semantics, closed-session guard, validation
- `PersonalMemoryTest` (12): unchanged from Phase 1
- `DefaultPersonalContextProviderTest` (10): session override, turn ordering, preferences, empty-context rules
- ITs (CI, Testcontainers): `JdbcCognitiveSessionStoreIT` (7 scenarios — one-active enforcement, tenant coexistence, upsert, user scoping), `JdbcUserPreferenceStoreIT` (4 scenarios)

### Files changed: 18

---

## Phase 4 — Grid Feedback Loop (Kafka) ✅

**Commit:** `feat(core): Phase 4 — Kafka feedback loop, Grid decisions become personal memories`

### What was done

**Domain (`core-domain`):**
- `DecisionOutcome` enum: CORRECT | INCORRECT | OVERRIDDEN
- `AgentDecisionFeedback` record: tenantId, userId, agentType, decisionSummary, outcome, confidence, engagementSignal (negative = absent), occurredAt; `hasEngagementSignal()` helper

**Processor (`core-memory`):**
- `AgentDecisionFeedbackProcessor` — the learning half of the Grid ↔ Core loop:
  - CORRECT → PROCEDURAL memory at full strength ("what worked for this user")
  - INCORRECT/OVERRIDDEN → PROCEDURAL memory at 0.6 strength (fades unless pattern repeats)
  - Engagement signal → EMOTIONAL memory: ENGAGED (≥0.66) / NEUTRAL (≥0.33) / DISENGAGED
  - Embedding service optional — zero vectors when Ollama disabled

**Kafka consumer (`core-api`):**
- `GridFeedbackListener` — `@KafkaListener` on `aether.core.feedback` topic (configurable topic + group-id); flat-JSON contract, field-by-field parsing; malformed messages logged and skipped (never wedges the consumer group)
- `GridFeedbackConfig` — `@ConditionalOnProperty(aether.core.feedback.enabled)`, **disabled by default** (Core runs standalone without Kafka)
- `spring-kafka` dependency; `spring.kafka.*` consumer config in application.yml

**Infrastructure:**
- Docker Compose: `kafka-core` service (apache/kafka 3.8, KRaft single node, healthcheck); aether-core wired with `FEEDBACK_ENABLED=true` + `KAFKA_BOOTSTRAP_SERVERS`

**Tests — 11 new, all green (42 total):**
- `AgentDecisionFeedbackProcessorTest` (6): outcome→memory mapping, strength rules, engagement banding
- `GridFeedbackListenerTest` (5): contract parsing, defaults, malformed/missing-field/unknown-outcome skip behaviour

### Files changed: 10
