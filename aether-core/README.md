# Aether Core

> Personal cognitive engine — individual memory, reasoning, and emotional context.

**Aether Core** is the personal intelligence layer of the [Aether ecosystem](https://github.com/suplab/aether-grid). It manages personal memories across four types (episodic, semantic, procedural, emotional), builds cognitive sessions from multi-turn interactions, and assembles personal context snapshots that power individual-aware AI agents.

**Sister repository:** [suplab/aether-grid](https://github.com/suplab/aether-grid) — the enterprise agent mesh. Grid's `AetherCoreBridgeAgent` calls Core's `GET /api/v1/personal-context/{tenantId}/{userId}` before agent decisions.

---

## Quick Start

```bash
cd core-infra/docker && docker compose up -d
cd ../.. && mvn spring-boot:run -pl core-api
# Core API: http://localhost:8082
# Health:   http://localhost:8082/actuator/health
```

## Modules

| Module | Purpose |
|---|---|
| `core-domain` | Domain types: PersonalMemory, CognitiveSession, PersonalContext, port interfaces |
| `core-memory` | pgvector store + Ollama embedding service (all-MiniLM-L6-v2, 384-dim) |
| `core-api` | Spring Boot REST API (port 8082) + Flyway migrations |
| `core-infra` | Docker Compose, standalone Flyway migrations |

## Key API Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/personal-context/{tenantId}/{userId}` | Personal context snapshot (consumed by Aether Grid) |
| `POST` | `/api/v1/users/{userId}/memories` | Store a new personal memory |
| `GET` | `/api/v1/users/{userId}/memories/count` | Memory count for a user |
| `DELETE` | `/api/v1/users/{userId}/memories/{memoryId}` | Delete a specific memory |
| `POST` | `/api/v1/tenants/{tenantId}/users/{userId}/sessions` | Start a cognitive session (closes prior active) |
| `PATCH` | `/api/v1/tenants/{tenantId}/users/{userId}/sessions/{sessionId}/turns` | Append a turn to a session |
| `POST` | `/api/v1/tenants/{tenantId}/users/{userId}/sessions/{sessionId}/close` | Close a session |
| `GET`/`PUT` | `/api/v1/users/{userId}/preferences` | Read / replace user preferences |
| `GET` | `/actuator/health` | Liveness + readiness probes |

## Memory Types

| Type | Description |
|---|---|
| `EPISODIC` | Specific events and experiences ("I met Alice at the conference") |
| `SEMANTIC` | Facts and general knowledge ("Alice is a machine learning engineer") |
| `PROCEDURAL` | How-to knowledge ("I prefer markdown over rich text") |
| `EMOTIONAL` | Emotional states and associations ("I feel anxious about deadlines") |

## Ecosystem

```
Aether Ecosystem
├── Aether Core  (suplab/aether-core)  ← you are here — personal cognitive engine
└── Aether Grid  (suplab/aether-grid)  — enterprise agent mesh and API governance
```

Aether Grid integrates with Core via `PersonalContextPort`. When `aether.core.base-url` is configured in Grid, its `AetherCoreHttpAdapter` fetches personal context from this service before each agent decision cycle.

See [suplab/aether-grid](https://github.com/suplab/aether-grid) for the enterprise integration layer.

---

## Configuration

| Environment Variable | Default | Description |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/aether_core` | PostgreSQL connection |
| `POSTGRES_USER` | `aether` | DB username |
| `POSTGRES_PASSWORD` | `aether` | DB password |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama embedding endpoint |
| `EMBEDDING_MODEL` | `all-minilm` | Embedding model name |
| `SERVER_PORT` | `8082` | HTTP port |
