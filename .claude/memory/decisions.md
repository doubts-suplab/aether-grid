# Architecture Decisions — Aether

Lightweight log of key decisions. Full ADRs are in `docs/adr/`.

---

## D-0NN — Consume the generic agent-harness; centralize the confidence gate
**Date:** 2026-07-23
**Status:** Accepted
**Decision:** `aether-agents` depends on the generic `agent-harness` Java binding rather than hand-rolling
agent execution. The confidence gate is centralized in `HarnessConfidenceGate` (delegating to the harness
`ConfidenceGate`); the duplicated `0.8` literal in `AgentOutput`/`GovernanceAgent`/`TemporalPredictionAgent`
is removed. `GovernanceAgent` routes through `Harness.invoke`.
**Rationale:** One testable gate, impossible for an agent to bypass; aligns Grid with the ecosystem-wide
harness protocol (AIEL authority ladder → BLOCK auto-enforces at ≥ 0.95, superseding the former flat 0.8).
**Trade-off:** BLOCK auto-enforcement threshold tightens from 0.8 to 0.95 (more human review). The binding is
resolved from the local Maven repo until a shared registry is configured. Remaining agents migrate incrementally.

---

## D-0NN — Honest per-agent authority ceilings (not blanket BLOCK)
**Date:** 2026-08-15
**Status:** Accepted
**Decision:** `HarnessRouting` wraps each agent at its true authority ceiling instead of a blanket
`AuthorityLevel.BLOCK` with all five actions. Ceilings: `Retry`/`Reflection`/`SelfImproving` → `SUGGEST`,
`Hallucination`/`Temporal` → `ALERT`, `AetherCoreBridge` → `OBSERVE`, `GovernanceAgent` → `BLOCK`; an
unprofiled agent fails safe to `OBSERVE`. The ceilings live in the one routing choke point so the whole
grid's authority policy is auditable in a single place.
**Rationale:** The harness already enforces `action-within-authority` (an over-authority action becomes a
security event + safe non-enforcing `DEFER`), so the previous blanket `BLOCK` silently gave every advisory
agent block authority — a conformance gap vs. the spec's static per-agent ceiling (§3.1). Now only
`GovernanceAgent` can auto-enforce a `BLOCK`; a suggestion-only agent's stray `BLOCK` is clamped to a safe
`DEFER`. Proven by `HarnessRoutingTest`.
**Trade-off:** A new agent must declare a ceiling (add a profile) to gain authority above `OBSERVE` — an
intentional fail-safe, not a regression.

---

## D-001 — Spring Cloud Gateway over custom Netty proxy
**Date:** 2026-06-14  
**Status:** Accepted  
**Decision:** Use Spring Cloud Gateway for the proxy layer.  
**Rationale:** Existing Spring ecosystem — reactive filter chain, dynamic route configuration, Resilience4j integration, Spring Security. Building a custom Netty proxy would duplicate this and add maintenance burden.  
**Trade-off:** Reactive programming model (Project Reactor) required throughout `aether-proxy`.

---

## D-002 — PGVector over Chroma as the default vector store
**Date:** 2026-06-14  
**Status:** Accepted  
**Decision:** pgvector (PostgreSQL extension) is the default `MemoryStore` adapter. Chroma is an alternative adapter.  
**Rationale:** One fewer container to operate. One backup policy. One failure domain. pgvector's IVFFlat index is sufficient for the expected memory record volume. Chroma adapter exists for teams with existing Chroma infrastructure.  
**Trade-off:** pgvector is less specialized than a dedicated vector DB; may hit limits at very large scale (>10M vectors per tenant).

---

## D-003 — Pluggable Agent SPI over switch-case dispatch
**Date:** 2026-06-14  
**Status:** Accepted  
**Decision:** Agents are discovered via Spring `List<Agent>` constructor injection into `AgentRegistry`. New agents require zero configuration.  
**Rationale:** Open/Closed principle. Agents are added by dropping a new `@Component` class — no changes to orchestrator, registry, or config files.  
**Trade-off:** Agent discovery is implicit; requires understanding the SPI pattern to know how to add agents.

---

## D-004 — Apache Kafka for event streaming
**Date:** 2026-06-14  
**Status:** Accepted  
**Decision:** Kafka is the event bus between proxy, memory, agents, and policy.  
**Rationale:** Temporal decoupling between proxy (request path) and agents (async reasoning). Event replay capability. Durable enough for the transactional outbox pattern. Proven at scale.  
**Trade-off:** Adds operational complexity (Zookeeper or KRaft, topic management). AWS SQS / EventBridge are viable alternatives for cloud-only deployments.

---

## D-005 — Spring EL (SpEL) for policy rule conditions
**Date:** 2026-06-14  
**Status:** Accepted  
**Decision:** Policy rules use SpEL expressions stored as YAML text in PostgreSQL.  
**Rationale:** No custom DSL to build or maintain. SpEL is already on the Spring classpath. Rules are human-readable YAML. Operators can change rules without redeployment.  
**Trade-off:** Risk of arbitrary code execution → mitigated by running in a sandboxed `StandardEvaluationContext` with an explicit variable allowlist. Only `ApiCall` properties are exposed.

---

## D-006 — Flyway over Liquibase for DB migrations
**Date:** 2026-06-14  
**Status:** Accepted  
**Decision:** Flyway with versioned SQL files (`V001__*.sql`) in `aether-infra/db/migration/`.  
**Rationale:** SQL-native (no XML/YAML migration files). Simple versioning model. Well-supported in Spring Boot auto-configuration. pgvector DDL (extension creation, `vector` column type) is easier to express in raw SQL than Liquibase change sets.  
**Trade-off:** No rollback support (Flyway Community). Migrations must be forward-only.

---

## D-007 — Modular monolith over distributed microservices (MVP)
**Date:** 2026-06-14  
**Status:** Accepted  
**Decision:** Two runnable Spring Boot apps (`aether-proxy`, `aether-api`); other modules are JARs. All share the same Postgres schema.  
**Rationale:** Distributed transactions add complexity that is premature at MVP. Module boundaries enforce DDD without network overhead. Future service extraction is possible because ports/adapters are already clean.  
**Trade-off:** Shared DB means schema migrations affect all modules. Scale-out is per-application, not per-bounded-context.

---

## D-008 — Transactional outbox for reliable Kafka publishing
**Date:** 2026-06-14  
**Status:** Accepted  
**Decision:** `ApiCall` rows and `outbox_events` rows are written in one DB transaction. A relay thread publishes to Kafka and marks rows as published.  
**Rationale:** Avoids the dual-write problem: if Kafka is temporarily unavailable, the event is not lost — it stays in `outbox_events` until the relay can deliver it.  
**Trade-off:** Slight delay (relay polling interval) between call capture and Kafka delivery. Adds `outbox_events` table and relay component.
