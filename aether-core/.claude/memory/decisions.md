# Architecture Decisions — Aether Core

## ADR-001: Spring Boot 3.3.x + Java 21
**Decision:** Use the same technology stack as Aether Grid.
**Rationale:** Ecosystem consistency, shared Java 21 features (records, sealed classes, virtual threads), same developer mental model across both repos. Grid and Core can share deployment tooling.

## ADR-002: Port 8082
**Decision:** Core API runs on port 8082.
**Rationale:** Grid proxy=8080, Grid api=8081, Core=8082. Clean port allocation within the ecosystem for local development without conflicts.

## ADR-003: Separate PostgreSQL database (`aether_core`)
**Decision:** Core uses its own `aether_core` database, not Grid's database.
**Rationale:** Data isolation — personal user memories must not be co-located with enterprise API governance data. Enables independent backup, retention, and GDPR policies per layer.

## ADR-004: REST over gRPC for Grid integration
**Decision:** `GET /api/v1/personal-context/{tenantId}/{userId}` over HTTP/1.1 REST.
**Rationale:** Simpler initial integration. Grid's `RestClient` adapter works without proto generation. HTTP/2 upgrade is optional later. gRPC can replace REST in Phase 4+ if latency becomes a concern.

## ADR-005: 384-dim embeddings (all-MiniLM-L6-v2)
**Decision:** Same embedding model and dimension as Aether Grid.
**Rationale:** If Grid and Core ever share a vector similarity search (e.g., cross-referencing personal memory against enterprise API patterns), vector dimensions must match. Consistent model = comparable similarity scores.

## ADR-006: Memory reinforcement on read
**Decision:** `PersonalMemory.reinforce()` is called each time a memory is retrieved. `strength += 0.1`, capped at 1.0. Access count incremented.
**Rationale:** Mirrors human memory — frequently accessed memories strengthen. Rarely accessed memories weaken (decay scheduled for Phase 5).

## ADR-007: Independent Maven project (not child of Grid's pom.xml)
**Decision:** `aether-core/pom.xml` is a standalone parent POM, not a module of `aether-grid/pom.xml`.
**Rationale:** Enables extraction to `suplab/aether-core` without any Maven refactoring. Each project builds and deploys independently.

## ADR-008: One ACTIVE cognitive session per user per tenant
**Decision:** A partial UNIQUE index (`(tenant_id, user_id) WHERE status = 'ACTIVE'`) enforces at most one active session. `JdbcCognitiveSessionStore.save()` closes prior active sessions before inserting a new active one.
**Rationale:** "Active session" must be unambiguous for PersonalContext assembly — Grid needs exactly one live cognitive state per user, and the database enforces the invariant rather than relying on application discipline.

## ADR-009: Session state overrides memory-derived state in PersonalContext
**Decision:** When an ACTIVE session exists, its `emotionalState` and `engagementScore` override values derived from EMOTIONAL/EPISODIC memories; session turn summaries are prepended to memory summaries.
**Rationale:** The live session is the freshest cognitive signal — a stored EMOTIONAL memory may be days old, while session turns reflect the current interaction.

## ADR-010: JSONB for turn summaries and preferences
**Decision:** `cognitive_sessions.turn_summaries` and `user_preferences.preferences` are JSONB columns serialised via Jackson, not normalised child tables.
**Rationale:** Turns and preferences are always read/written as whole documents with the parent row — no per-turn queries exist. Avoids join overhead and keeps replace-on-save semantics trivial.

## ADR-011: Phases 4 and 5 delivered before Phase 3 (GDPR)
**Decision:** Grid Feedback Loop (Kafka) and Memory Decay Scheduler shipped ahead of GDPR controls, by explicit prioritisation. Phase 3 takes migration V006 (V005 consumed by the archive table).
**Rationale:** The learning loop and lifecycle are Core's differentiating behaviours; GDPR endpoints are additive and don't affect the schema built so far.

## ADR-012: Kafka feedback consumer disabled by default
**Decision:** `GridFeedbackConfig` is `@ConditionalOnProperty(aether.core.feedback.enabled)` with no `matchIfMissing` — the consumer only starts when explicitly enabled.
**Rationale:** Core must run standalone without Kafka or Grid present (same principle as the optional embedding service). Docker Compose enables it because Kafka ships in the stack.

## ADR-013: Archive via data-modifying CTE, never DELETE-only
**Decision:** Faded memories are moved to `personal_memories_archive` with a single `WITH moved AS (DELETE … RETURNING …) INSERT …` statement.
**Rationale:** Atomicity without a transaction manager dependency — a memory can never be deleted without landing in the archive. Forgetting is graceful and reversible; embeddings are retained for potential restore.
