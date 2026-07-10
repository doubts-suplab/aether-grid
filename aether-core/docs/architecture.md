# Aether Core — Architecture

> **Sister repository:** [suplab/aether-grid](https://github.com/suplab/aether-grid) — the enterprise agent mesh that consumes Core's personal context API.

---

## Overview

Aether Core is the personal cognitive engine of the Aether ecosystem. It stores individual memories across four types, assembles personal context snapshots on-demand, and exposes a REST API that Aether Grid agents call to enrich enterprise decisions with individual user context.

```
Aether Grid (suplab/aether-grid)
      │
      │  GET /api/v1/personal-context/{tenantId}/{userId}
      ▼
Aether Core (suplab/aether-core)          port 8082
      │
      ├── PersonalContextController
      │       └── PersonalContextProvider (port)
      │               └── DefaultPersonalContextProvider (adapter)
      │                       ├── PersonalMemoryStore     → PGVectorPersonalMemoryStore
      │                       │                              └── PostgreSQL 16 + pgvector
      │                       ├── CognitiveSessionStore   → JdbcCognitiveSessionStore
      │                       └── UserPreferenceStore     → JdbcUserPreferenceStore
      │
      ├── PersonalMemoryController
      │       ├── PersonalMemoryStore (port)
      │       └── PersonalEmbeddingService
      │               └── Ollama (all-MiniLM-L6-v2, 384-dim)
      │
      ├── CognitiveSessionController
      │       └── CognitiveSessionStore (port) — create / list / add turn / close
      │
      └── UserPreferenceController
              └── UserPreferenceStore (port) — get / replace
```

---

## Module Boundaries

### `core-domain` — Pure Domain (No Spring)

Contains all domain types and port interfaces. Zero Spring dependencies — fully unit-testable without a context.

```
com.suplab.aether.core.domain
  PersonalMemory        — record: id, userId, type, content, strength, accessCount, timestamps
                          create() factory · reinforce() → strength+0.1 capped at 1.0
  MemoryType            — enum: EPISODIC | SEMANTIC | PROCEDURAL | EMOTIONAL
  CognitiveSession      — record: sessionId, userId, tenantId, turnSummaries, emotionalState,
                          engagementScore, status, timestamps
                          start() factory · withTurn() appends + updates state · close()
  SessionStatus         — enum: ACTIVE | CLOSED (one ACTIVE per user per tenant)
  PersonalContext       — record: assembled snapshot served to Grid

com.suplab.aether.core.ports
  PersonalMemoryStore     — driven port: save, findSimilar, findByType, delete, countByUser
  PersonalContextProvider — driven port: buildContext(tenantId, userId)
  CognitiveSessionStore   — driven port: save, findById, findActive, findByUser
  UserPreferenceStore     — driven port: find, save (replace semantics)
```

### `core-memory` — Persistence Adapters

Implements the port interfaces from `core-domain`. Depends on Spring JDBC and pgvector.

```
com.suplab.aether.core.memory.store
  PGVectorPersonalMemoryStore  — implements PersonalMemoryStore
    • save(): upsert with vector embedding
    • findSimilar(): cosine similarity (<=>), ORDER BY distance, LIMIT
    • findByType(): filtered by memory_type, ORDER BY strength DESC
    • Reinforce-on-read: every retrieval strengthens the memory (+0.1 capped at 1.0,
      accessCount+1) and persists the reinforced state immediately

com.suplab.aether.core.memory.context
  DefaultPersonalContextProvider  — implements PersonalContextProvider
    • Assembles PersonalContext from memories + active session + preferences
    • Active session's emotionalState/engagementScore override memory-derived values
    • Session turn summaries prepended to memory summaries
    • Optional.empty() when the user has no cognitive data at all

com.suplab.aether.core.memory.session
  JdbcCognitiveSessionStore  — implements CognitiveSessionStore
    • Turn summaries stored as a JSONB array
    • Saving an ACTIVE session closes the user's previous active session in the tenant

com.suplab.aether.core.memory.preference
  JdbcUserPreferenceStore  — implements UserPreferenceStore
    • One JSONB document per user, replace-on-save semantics

com.suplab.aether.core.memory.embedding
  PersonalEmbeddingService  — Ollama RestClient adapter
    • embed(text) → float[384]
    • Graceful fallback: returns zero vector on Ollama unavailability
    • Conditional bean: aether.core.embedding.enabled=false runs Core without Ollama
```

### `core-api` — Spring Boot Application

Running application (port 8082). Wires all modules, exposes REST endpoints, runs Flyway.

```
com.suplab.aether.core.api
  AetherCoreApplication  — @SpringBootApplication, port 8082

com.suplab.aether.core.api.controller
  PersonalContextController   — GET /api/v1/personal-context/{tenantId}/{userId}
  PersonalMemoryController    — POST/GET/DELETE /api/v1/users/{userId}/memories
  CognitiveSessionController  — /api/v1/tenants/{tenantId}/users/{userId}/sessions
                                POST create · GET list · GET {sessionId}
                                PATCH {sessionId}/turns · POST {sessionId}/close
  UserPreferenceController    — GET/PUT /api/v1/users/{userId}/preferences

com.suplab.aether.core.api.config
  CoreApiConfig  — @Bean wiring: PersonalMemoryStore, CognitiveSessionStore,
                   UserPreferenceStore, PersonalContextProvider, PersonalEmbeddingService
```

### `core-infra` — Infrastructure

Docker Compose for local dev, standalone Flyway migrations.

---

## Database Schema

### `personal_memories` table

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK, `gen_random_uuid()` |
| `user_id` | `TEXT` | Scoped per user — never cross-user queries |
| `memory_type` | `TEXT` | CHECK IN ('EPISODIC','SEMANTIC','PROCEDURAL','EMOTIONAL') |
| `content` | `TEXT` | Raw memory text |
| `embedding` | `vector(384)` | all-MiniLM-L6-v2 cosine-similarity index |
| `strength` | `DOUBLE PRECISION` | 0.0–1.0, reinforced on access |
| `access_count` | `INTEGER` | Incremented on each read |
| `created_at` | `TIMESTAMPTZ` | Immutable |
| `last_accessed_at` | `TIMESTAMPTZ` | Updated on reinforce |

**Indexes:**
- `idx_personal_memories_user_id` — `(user_id)` for per-user queries
- `idx_personal_memories_user_type` — `(user_id, memory_type)` for type-filtered queries
- `idx_personal_memories_embedding` — `ivfflat (embedding vector_cosine_ops)`, lists=100

### `cognitive_sessions` table (V003)

| Column | Type | Notes |
|---|---|---|
| `session_id` | `UUID` | PK, `gen_random_uuid()` |
| `user_id` | `TEXT` | Scoped per user |
| `tenant_id` | `TEXT` | Tenant isolation |
| `turn_summaries` | `JSONB` | Array of one-line turn summaries |
| `emotional_state` | `TEXT` | Latest observed state, default NEUTRAL |
| `engagement_score` | `DOUBLE PRECISION` | 0.0–1.0 |
| `status` | `TEXT` | CHECK IN ('ACTIVE','CLOSED') |
| `started_at` | `TIMESTAMPTZ` | Immutable |
| `last_active_at` | `TIMESTAMPTZ` | Updated on every turn |

**Indexes:**
- `idx_cognitive_sessions_user` — `(tenant_id, user_id, last_active_at DESC)`
- `idx_cognitive_sessions_one_active` — partial UNIQUE `(tenant_id, user_id) WHERE status = 'ACTIVE'` — enforces one active session per user per tenant

### `user_preferences` table (V004)

| Column | Type | Notes |
|---|---|---|
| `user_id` | `TEXT` | PK |
| `preferences` | `JSONB` | Free-form key/value document, replace-on-save |
| `updated_at` | `TIMESTAMPTZ` | Updated on save |

---

## PersonalContext API Contract

This is the contract between Aether Core and Aether Grid. **Breaking changes require Grid-side updates.**

```
GET /api/v1/personal-context/{tenantId}/{userId}?memoryLimit=5

Response 200:
{
  "userId": "user-42",
  "tenantId": "acme-corp",
  "recentMemorySummaries": [
    "Presented Q3 roadmap to stakeholders",
    "Prefers async communication over meetings"
  ],
  "preferences": { "communication-style": "async" },
  "emotionalState": "MOTIVATED",
  "engagementScore": 0.82,
  "fetchedAt": "2026-06-15T08:00:00Z"
}
```

**Assembly rules (Phase 2):**
- If the user has an ACTIVE cognitive session, its `emotionalState` and `engagementScore` override memory-derived values, and its turn summaries appear first in `recentMemorySummaries`.
- `preferences` is populated from the `user_preferences` table.
- A user with no memories, no active session, and no preferences receives a neutral default context (NEUTRAL, 0.5) — the endpoint always returns HTTP 200.

---

## Grid Feedback Loop (Kafka)

Grid publishes decision outcomes to the `aether.core.feedback` topic; Core turns them into personal memories.

```
Aether Grid ──publish──▶ Kafka topic: aether.core.feedback
                              │
                              ▼
              GridFeedbackListener (core-api, @KafkaListener)
                              │  flat JSON → AgentDecisionFeedback
                              ▼
              AgentDecisionFeedbackProcessor (core-memory)
                    ├── CORRECT outcome      → PROCEDURAL memory (strength 1.0)
                    ├── INCORRECT/OVERRIDDEN → PROCEDURAL memory (strength 0.6)
                    └── engagementSignal     → EMOTIONAL memory
                                               (ENGAGED ≥0.66 / NEUTRAL ≥0.33 / DISENGAGED)
```

**Message contract** (flat JSON — no shared DTO module, mirrors the REST approach):

```json
{
  "tenantId": "acme-corp",
  "userId": "user-42",
  "agentType": "GovernanceAgent",
  "decisionSummary": "Approved elevated API quota for analytics batch job",
  "outcome": "CORRECT",
  "confidence": 0.91,
  "engagementSignal": 0.8,
  "occurredAt": "2026-07-10T08:00:00Z"
}
```

`engagementSignal` and `occurredAt` are optional. Malformed messages are logged and skipped.
The consumer is **disabled by default** (`aether.core.feedback.enabled=false`) — Core must run standalone without Kafka or Grid present.

---

## Aether Grid Integration

Grid's `AetherCoreBridgeAgent` (in `suplab/aether-grid`) calls this endpoint before agent decisions. Configuration in Grid:

```yaml
aether:
  core:
    base-url: http://aether-core:8082
    api-key: ${AETHER_CORE_API_KEY}
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
```

Grid's `AetherCoreHttpAdapter` has a Resilience4j circuit breaker (`aether-core`) — when Core is unavailable, Grid falls back to `Optional.empty()` and proceeds without personal context (degraded but not blocked).

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (`--enable-preview`) |
| Framework | Spring Boot 3.3.5 (`jakarta.*`) |
| Database | PostgreSQL 16 + pgvector |
| Vector Search | pgvector, 384-dim, ivfflat cosine index |
| Embedding | all-MiniLM-L6-v2 via Ollama |
| DB Migrations | Flyway |
| Build | Maven multi-module |
| Local Dev | Docker Compose |
