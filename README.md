# Æ Aether Grid

> Distributed intelligence mesh — enterprise AI agent governance platform

**Aether Grid** is the enterprise layer of the [Aether ecosystem](https://github.com/suplab/aether-core#ecosystem). It sits as a smart proxy and governance layer in front of any API ecosystem, orchestrating a mesh of specialised AI agents that learn, self-improve, and enforce policy across all API interactions.

**Sister repository:** [suplab/aether-core](https://github.com/suplab/aether-core) — the personal cognitive engine that powers individual user context enrichment in Grid agents.

**Author:** Suplab &nbsp;|&nbsp; **Status:** All 15 Phases Complete — Production Ready &nbsp;|&nbsp; **Phases Complete:** 0–15 of 15

---

## What Aether Grid Does

Aether Grid is implemented as a **Maven multi-module Spring Boot 3.x / Java 21** application. It operates as:

- **Remembers** every API interaction semantically (all-MiniLM-L6-v2 embeddings + metadata in pgvector, 384-dim)
- **Learns** patterns of successful and failing requests over time (PROCEDURAL/SEMANTIC/EPISODIC/EMOTIONAL memory types)
- **Governs** API usage via YAML policies stored in PostgreSQL and evaluated at runtime with SpEL
- **Predicts** temporal failure and latency windows with the `TemporalPredictionAgent`
- **Debugs itself**: `HallucinationDetectorAgent` validates LLM outputs, `ReflectionAgent` evaluates system health
- **Improves itself**: `SelfImprovingAgent` reads `AgentFeedback` history and generates LLM-powered improvement suggestions; `AgentLearningService` reviews all tenants weekly on a schedule
- **Tracks performance**: `AgentController` exposes feedback recording (`POST /api/v1/tenants/{tenantId}/agents/feedback`) and per-agent performance stats (`GET /api/v1/tenants/{tenantId}/agents/performance`)
- **Coordinates** six specialized agents across a cognitive mesh with shared pgvector memory
- **Provides visibility**: live dark-theme operator dashboard at `http://localhost:8081/dashboard.html` — stat cards, agent registry, memory breakdown, decision history, SSE live stream
- **Protects PII** via `GdprRedactionService` (email, phone, cards, SSN, JWT, API keys) before any persistence
- **Enforces confidence gates**: the centralized [agent-harness](https://github.com/doubts-suplab/agent-harness) `ConfidenceGate` auto-enforces a `BLOCK` only at confidence ≥ 0.95; anything lower requires human-in-the-loop

> **Sister repository:** [Aether Core](https://github.com/suplab/aether-core) (`suplab/aether-core`) — the personal cognitive engine. Aether Grid integrates with Core via `PersonalContextPort` to enrich agent decisions with individual user context.

---

## Repository Structure

```
aether-grid/
├── .claude/
│   ├── agents/                    # 19 specialist agents (eeik-bootstrap)
│   ├── commands/                  # 5 slash commands (/estimate, /review, /adr, /memory-update, /security-scan)
│   ├── hooks/                     # Pre-write, pre-bash, post-edit, on-stop hooks
│   └── memory/
│       ├── project-context.md     # Service inventory, ports, environments
│       ├── domain-glossary.md     # Aether-specific terminology
│       ├── decisions.md           # Architecture Decision Records log
│       ├── constraints.md         # Hard constraints + golden rules
│       ├── patterns.md            # Approved patterns (agent SPI, outbox, policy-as-code)
│       ├── tech-debt.md           # Known debt tracker
│       └── session-log.md         # Rolling session log
├── .github/
│   ├── instructions/              # Copilot glob-based instructions
│   ├── owasp-suppressions.xml     # OWASP Dependency-Check accepted false positives
│   └── workflows/
│       ├── ci.yml                 # Every push: Temurin 21, Maven verify, Postgres service, JaCoCo report
│       ├── quality-gate.yml       # PRs to main: Checkstyle (google_checks.xml) + OWASP (failBuildOnCVSS=9)
│       ├── docker-build.yml       # OIDC, matrix [aether-api, aether-proxy], amd64+arm64, GHCR push
│       └── helm-release.yml       # helm lint (3 values files) + dry-run + OCI push to ghcr.io/suplab/helm
├── docs/
│   ├── index.html                 # Visual concept page (always in sync)
│   ├── architecture.md            # Technical architecture deep-dive
│   ├── roadmap.md                 # Phased delivery plan
│   ├── progress.md                # Live progress tracker
│   └── adr/                       # Architecture Decision Records
│       ├── 001-spring-cloud-gateway.md
│       ├── 002-pgvector-over-chroma.md
│       ├── 003-pluggable-agent-spi.md
│       ├── 004-kafka-event-bus.md
│       ├── 005-spel-policy-rules.md
│       └── 006-flyway-migrations.md
│
├── aether-domain/                 # Shared domain models, events, port interfaces
│   └── src/main/java/com/suplab/aether/core/
│       ├── domain/                # ApiCall, Tenant, value objects (Java records)
│       ├── events/                # Sealed DomainEvent hierarchy
│       ├── ports/                 # MemoryStore, EventPublisher, PolicyRepository
│       └── exceptions/            # AetherException hierarchy
│
├── aether-proxy/                  # Spring Cloud Gateway — Data Plane (port 8080)
│   └── src/main/java/com/suplab/aether/proxy/
│       ├── filter/                # TenantAuthFilter (order=-100), RedactionFilter (order=-90),
│       │                          # ApiCallCaptureFilter (order=-50)
│       ├── outbox/                # JdbcOutboxRepository, OutboxRelayScheduler (5s interval)
│       ├── ratelimit/             # TenantKeyResolver (Redis per-tenant key)
│       └── tenant/                # JdbcTenantRepository
│
├── aether-memory/                 # Embedding service + vector storage
│   └── src/main/java/com/suplab/aether/memory/
│       ├── embedding/             # OllamaEmbeddingService (all-MiniLM-L6-v2, 384-dim)
│       ├── store/                 # PGVectorMemoryStore (<=> cosine operator, strength reinforcement)
│       ├── lifecycle/             # MemoryLifecycleService (daily decay, weekly purge)
│       └── consumer/              # ApiCallMemoryConsumer (Kafka listener, memory type classifier)
│
├── aether-agents/                 # Agent subsystem
│   └── src/main/java/com/suplab/aether/agents/
│       ├── spi/                   # Agent interface, AgentCapability, AgentInput/Output, AgentDecision
│       ├── registry/              # AgentRegistry (Spring List<Agent> injection, disableAgent kill-switch)
│       ├── orchestrator/          # AgentOrchestrator (VirtualThreads, MAX_ITERATIONS=5, Micrometer metrics)
│       ├── governance/            # GovernanceAgent (LLM JSON protocol, confidence gate)
│       ├── retry/                 # RetryAgent (failure memory counts, exponential backoff)
│       ├── hallucination/         # HallucinationDetectorAgent (memory pattern validation)
│       ├── temporal/              # TemporalPredictionAgent (EPISODIC/SEMANTIC counts, ALERT/DEFER)
│       ├── reflection/            # ReflectionAgent (procedural health score, SUGGEST/DEFER)
│       ├── selfimproving/         # SelfImprovingAgent (feedback history → LLM suggestions, SUGGEST)
│       └── llm/                   # LlmClient interface + OllamaLlmClient + GroqLlmClient + AnthropicLlmClient
│
├── aether-policy/                 # Policy engine
│   └── src/main/java/com/suplab/aether/policy/
│       ├── model/                 # PolicyRule, PolicyEvaluationContext, PolicyEvaluationResult
│       ├── engine/                # SpelPolicyEngine (SimpleEvaluationContext, read-only sandbox)
│       ├── storage/               # JdbcPolicyRepository (single-active invariant, auto-versioning)
│       └── audit/                 # AuditLogService (JSONB, no FK), GdprRedactionService (regex PII)
│
├── aether-api/                    # Admin REST API — Control Plane (port 8081)
│   └── src/main/java/com/suplab/aether/api/
│       ├── controller/            # TenantController, PolicyController, MemoryController
│       │                          # AuditController, AgentController, DashboardController
│       │                          # GlobalExceptionHandler (RFC 7807 ProblemDetail)
│       ├── service/               # AgentLearningService (@Scheduled weekly), DashboardStatsService
│       ├── config/                # ApiConfig, LearningConfig
│       ├── security/              # SecurityConfig (JWT OAuth2; /dashboard/** and /*.html open)
│       └── resources/static/      # dashboard.html — self-contained operator SPA
│
├── aether-infra/                  # Infrastructure-as-Code (no Java source)
│   ├── docker/
│   │   ├── docker-compose.yml     # Full local stack (Postgres+pgvector, Redis, Kafka,
│   │   │                          # Zookeeper, Ollama, Prometheus, Grafana)
│   │   ├── docker-compose.test.yml # CI-only (lighter, no Ollama)
│   │   └── .env.example           # All env variable names (no values committed)
│   ├── db/migration/              # Flyway SQL migrations V001–V012
│   │   ├── V001__create_tenants.sql
│   │   ├── V002__create_endpoints.sql
│   │   ├── V003__create_api_calls.sql
│   │   ├── V004__create_memory_embeddings.sql   # vector(384) column
│   │   ├── V005__create_policies.sql
│   │   ├── V006__create_policy_versions.sql
│   │   ├── V007__create_agent_decisions.sql
│   │   ├── V008__create_audit_log.sql
│   │   ├── V009__create_outbox_events.sql
│   │   ├── V010__tenant_gdpr_preferences.sql    # memory_opt_out, data_retention_days
│   │   ├── V011__row_level_security.sql         # PG RLS on all tenant-scoped tables
│   │   └── V012__agent_feedback.sql             # agent_feedback table, RLS, index
│   ├── k8s/                       # Kubernetes manifests
│   │   ├── namespace.yaml         # aether-grid namespace
│   │   ├── secrets-template.yaml  # Required Secret keys (no values committed)
│   │   ├── aether-api/            # Deployment, Service, HPA (min 2/max 8), ConfigMap
│   │   └── aether-proxy/          # Deployment, Service, HPA (min 2/max 16), ConfigMap
│   ├── helm/aether-grid/          # Helm chart (20 templates)
│   │   ├── values.yaml            # Cloud-agnostic defaults (GHCR, nginx ingress, 2 replicas)
│   │   ├── values-aws.yaml        # EKS overrides: ALB, IRSA, ECR, ExternalDNS
│   │   └── values-openshift.yaml  # OCP overrides: Quay.io, Route (edge TLS), no Ingress
│
├── CLAUDE.md                      # Project brief (eeik-bootstrap template)
├── aether.manifest.yaml           # EEIK project manifest
├── pom.xml                        # Parent Maven POM (multi-module)
└── README.md                      # This file
```

---

## Architecture Overview

```
                    ┌──────────────────────────────────────┐
                    │           Aether Grid                 │
                    │                                       │
  Incoming  ──────► │  aether-proxy  (port 8080)           │
  API calls         │  Spring Cloud Gateway                 │
                    │  Tenant routing · Circuit breaker     │
                    │  Rate limiting · Call capture         │
                    └──────────────┬───────────────────────┘
                                   │ ApiCallRecordedEvent (Kafka)
                    ┌──────────────▼───────────────────────┐
                    │           Event Bus (Kafka)           │
                    └───┬───────────────┬──────────────────┘
                        │               │
          ┌─────────────▼──┐   ┌────────▼──────────────────┐
          │ aether-memory   │   │       aether-agents        │
          │ EmbeddingService│   │  AgentRegistry             │
          │ PGVector store  │   │  AgentOrchestrator         │
          │ Knowledge graph │   │  GovernanceAgent           │
          │ Compaction job  │   │  RetryAgent                │
          └─────────────────┘   │  HallucinationAgent        │
                                │  PolicyDriftAgent          │
          ┌─────────────────┐   │  TemporalPredictionAgent   │
          │ aether-policy   │◄──┤  ReflectionAgent           │
          │ Policy-as-Code  │   └────────────────────────────┘
          │ SpEL evaluator  │
          │ GDPR redaction  │
          │ Audit log       │
          └────────┬────────┘
                   │
          ┌────────▼────────┐
          │  aether-api     │  (port 8081)
          │  Admin REST API │
          │  Tenant mgmt    │
          │  OpenTelemetry  │
          └─────────────────┘
```

---

## Agent Mesh

| Agent | Capability | Built | Role |
|---|---|---|---|
| **GovernanceAgent** | `GOVERNANCE` | Phase 7 | LLM JSON response protocol; ALLOW/BLOCK/ALERT decisions; runs on the agent-harness — BLOCK auto-enforces at ≥ 0.95, else human-in-the-loop |
| **RetryAgent** | `RETRY_OPTIMIZATION` | Phase 7 | Counts failure/timeout memories; suggests exponential backoff; fast-path for zero-failure calls |
| **HallucinationDetectorAgent** | `HALLUCINATION_DETECTION` | Phase 7 | Validates LLM outputs against stored memory patterns; defaults ALERT when LLM unavailable |
| **TemporalPredictionAgent** | `TEMPORAL_PREDICTION` | Phase 10 | Analyses EPISODIC/SEMANTIC memory counts; LLM ALERT/DEFER predictions; fast-path DEFER for zero memories |
| **ReflectionAgent** | `REFLECTION` | Phase 10 | Procedural health score = `proceduralCount / (total + 1)`; fast-path ALLOW when healthy; LLM SUGGEST when poor |
| **SelfImprovingAgent** | `SELF_IMPROVEMENT` | Phase 13 | Meta-agent; reads `AgentFeedback` history; builds LLM prompt with outcome statistics; returns improvement suggestions as SUGGEST decisions |

All agents implement the `Agent` SPI and are auto-discovered via `AgentRegistry` (Spring `List<Agent>` injection, `registeredTypes()` for dashboard enumeration). Zero configuration to add a new agent. The `AgentOrchestrator` records `aether.agent.executions` and `aether.agent.latency` Micrometer metrics per execution.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.x (`jakarta.*` exclusively) |
| API Gateway | Spring Cloud Gateway |
| Messaging | Apache Kafka + transactional outbox |
| Database | PostgreSQL 16 + pgvector extension |
| Cache / Rate Limiting | Redis 7 |
| Vector Store | pgvector (default), Chroma (adapter) |
| LLM Runtime | Ollama (default, local) · Groq cloud · Anthropic Claude — swappable via `AETHER_LLM_PROVIDER` env var |
| Embedding Model | all-MiniLM-L6-v2 via Ollama (384-dim, fixed — changing requires full re-embedding migration) |
| Resilience | Resilience4j (circuit breaker, retry, bulkhead) |
| Policy Rules | Spring EL (SpEL) evaluated against YAML policies in PostgreSQL |
| Observability | OpenTelemetry + Micrometer + Prometheus + Grafana |
| DB Migrations | Flyway |
| Build | Maven (multi-module) |
| Local Dev | Docker Compose |
| Production | Kubernetes + Helm |
| CI/CD | GitHub Actions (OIDC, no static secrets) |

---

## Use Cases

- **Enterprise API Governance** — enforce, audit, and evolve API usage policies organisation-wide
- **Engineering Intelligence Platform** — code, architecture, documentation, and release agents
- **Agentic AI Infrastructure** — shared memory and orchestration backbone for any multi-agent system
- **Insurance Claims Platform** — claim analysis, fraud detection, settlement, and communication agents

---

## Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Agent hallucination | Confidence gate (agent-harness): BLOCK auto-enforces only at ≥ 0.95 → else human-in-the-loop; HallucinationDetectorAgent validates all outputs |
| Data growth | Monthly `MemoryCompactionJob` summarises old memories; pgvector index pruning |
| Latency | Policy checks async; blocking only on high-confidence enforcement |
| Privacy / GDPR | `GdprRedactionService` strips PII before any persistence; opt-out and erasure endpoints |
| Policy drift | `PolicyDriftAgent` monitors behavioral divergence continuously |
| LLM vendor lock-in | `LlmClient` interface — `OllamaLlmClient` is one adapter; swap freely |

---

## Quick Start

```bash
# Start the full local infrastructure stack
# (PostgreSQL+pgvector, Redis, Kafka, Ollama, Prometheus, Grafana)
docker compose -f aether-infra/docker/docker-compose.yml up -d

# Build all modules (requires Java 21, Maven 3.9+)
mvn verify

# Admin API — Swagger UI (control plane, port 8081)
open http://localhost:8081/api/swagger-ui.html

# Operator dashboard (no auth required)
open http://localhost:8081/dashboard.html

# Onboard a tenant
curl -s -X POST http://localhost:8081/api/v1/tenants \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"name":"my-tenant","apiKey":"<raw-key>"}'

# Proxy an API call (data plane, port 8080)
curl -H "X-API-Key: <raw-key>" \
     http://localhost:8080/<configured-path>

# Grafana dashboards
open http://localhost:3000
```

**Kubernetes deployment** (production via Helm):

```bash
# Vanilla Kubernetes
helm install aether-grid aether-infra/helm/aether-grid

# AWS EKS (ALB Ingress Controller, IRSA, ECR)
helm install aether-grid aether-infra/helm/aether-grid \
  -f aether-infra/helm/aether-grid/values-aws.yaml

# OpenShift (Route edge TLS, Quay.io, OCP securityContext)
helm install aether-grid aether-infra/helm/aether-grid \
  -f aether-infra/helm/aether-grid/values-openshift.yaml
```

Raw Kubernetes manifests are also available for direct `kubectl apply`:

```bash
kubectl apply -f aether-infra/k8s/namespace.yaml
kubectl apply -f aether-infra/k8s/
kubectl get pods -n aether-grid
```

See `aether-infra/k8s/secrets-template.yaml` for the full list of required secret keys.

**LLM provider selection** (set before starting `aether-proxy` or `aether-api`):

```bash
# Default: local Ollama
export AETHER_LLM_PROVIDER=ollama

# Groq cloud (fast remote inference)
export AETHER_LLM_PROVIDER=groq
export GROQ_API_KEY=<key>

# Anthropic Claude
export AETHER_LLM_PROVIDER=anthropic
export ANTHROPIC_API_KEY=<key>
```

---

## Documentation

| Document | Description |
|---|---|
| [Concept & Vision](docs/index.html) | Visual overview of Aether Grid |
| [Architecture](docs/architecture.md) | Technical deep-dive: modules, patterns, data model, security |
| [Roadmap](docs/roadmap.md) | Phased delivery plan (Phase 0–15) |
| [Progress](docs/progress.md) | Live development progress tracker |
| [ADRs](docs/adr/) | Architecture Decision Records |

---

## Contributing

All development follows [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(proxy): add tenant-aware rate limiting
fix(memory): correct cosine similarity threshold
docs(roadmap): mark Phase 2 complete
chore(bootstrap): update .claude/memory/decisions.md
```

Branch from `main`, open a PR. CI must be green. No `// TODO` in committed code.

---

<p align="center">
  <sub>
    Scaffolded and governed by
    <a href="https://github.com/suplab/eeik-bootstrap"><strong>eeik-bootstrap</strong></a>
    — the AI-native enterprise engineering operating system.<br/>
    19 specialist agents · 5 slash commands · persistent memory · governance from day one.
  </sub>
</p>
