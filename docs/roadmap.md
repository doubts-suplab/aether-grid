# Aether — Development Roadmap

> Living document. Updated with each phase completion. Last updated: Phase 15.

---

## Guiding Principles

- **Small chunks** — each phase is a logical unit committable in one or few commits
- **Docs in sync** — every commit that changes behavior also updates README, this file, index.html, and progress.md
- **Working software first** — infrastructure before features; domain model before adapters
- **EEIK-governed** — all development follows eeik-bootstrap golden rules (constructor injection, no secrets, SLF4J, explicit SQL columns, `jakarta.*`, Conventional Commits)

---

## Status Legend

| Symbol | Meaning |
|---|---|
| ✅ | Complete |
| 🔄 | In Progress |
| 📋 | Planned |
| ⏸ | Deferred |

---

## Phase 0 — Concept & Documentation ✅

**Goal:** Establish the full Aether vision, enterprise-grade documentation, and project structure.

| Deliverable | Status |
|---|---|
| README.md — full rewrite scoping Aether Grid; ecosystem overview links to sister repos | ✅ |
| docs/index.html — visual concept page | ✅ |
| docs/architecture.md — technical deep-dive | ✅ |
| docs/roadmap.md — this file | ✅ |
| docs/progress.md — live tracker | ✅ |

**Commit:** `docs: establish aether ecosystem concept, architecture, and project roadmap`

---

## Phase 1 — EEIK Bootstrap Integration ✅

**Goal:** Every Claude Code session has full enterprise AI governance context from the first prompt.

| Deliverable | Status |
|---|---|
| `CLAUDE.md` — AetherGrid project brief with tech stack, golden rules, slash commands | ✅ |
| `.claude/memory/project-context.md` — service inventory, ports, auth, environments | ✅ |
| `.claude/memory/domain-glossary.md` — 33 AetherGrid-specific terms | ✅ |
| `.claude/memory/decisions.md` — 8 key architectural decisions (D-001 through D-008) | ✅ |
| `.claude/memory/constraints.md` — 10 golden rules + 8 Aether-specific hard constraints | ✅ |
| `.claude/memory/patterns.md` — 8 approved patterns with code examples | ✅ |
| `.claude/memory/tech-debt.md` — debt register (initialized empty) | ✅ |
| `.claude/memory/session-log.md` — rolling session log | ✅ |
| `.claude/hooks/` — 4 safety hooks (pre-bash-guard, pre-write-guard, post-edit-check, on-stop) | ✅ |
| `.claude/commands/` — 5 slash commands (/estimate, /review, /adr, /memory-update, /security-scan) | ✅ |
| `.claude/agents/` — 19 specialist agents | ✅ |
| `aether.manifest.yaml` — EEIK project manifest | ✅ |

**Commit:** `chore(bootstrap): integrate eeik governance layer — CLAUDE.md, memory, hooks, commands`

---

## Phase 2 — Maven Multi-Module Foundation ✅

**Goal:** `mvn validate` passes across all modules. Module boundaries enforce bounded contexts.

| Deliverable | Status |
|---|---|
| `pom.xml` — parent POM (Java 21, Spring Boot 3.3.5 BOM, Spring Cloud 2023.0.3, Resilience4j 2.2.0, Testcontainers BOM) | ✅ |
| `-parameters` compiler flag — required for Spring MVC path variable name resolution | ✅ |
| JaCoCo 80% line coverage gate | ✅ |
| Maven Enforcer plugin (Java 21+, Maven 3.9+) | ✅ |
| `aether-domain/pom.xml` — pure domain module (no Spring) | ✅ |
| `aether-memory/pom.xml` — spring-data-jdbc, postgresql, pgvector, spring-kafka | ✅ |
| `aether-agents/pom.xml` — RestClient for Ollama, jackson-databind, micrometer-core | ✅ |
| `aether-policy/pom.xml` — spring-data-jdbc, jackson-dataformat-yaml, flyway | ✅ |
| `aether-proxy/pom.xml` — spring-cloud-gateway, resilience4j, spring-data-redis-reactive | ✅ |
| `aether-api/pom.xml` — spring-security, oauth2-resource-server, springdoc-openapi 2.6.0, OTel | ✅ |
| `aether-infra/pom.xml` — pom packaging only (no Java source) | ✅ |
| `AetherProxyApplication` skeleton (port 8080) + `application.yml` | ✅ |
| `AetherApiApplication` skeleton (port 8081) + `application.yml` | ✅ |

**Commit:** `build(infra): add parent POM and 7-module Maven structure`

**Verification:** `mvn validate` — passed clean.

---

## Phase 3 — Infrastructure Stack ✅

**Goal:** `docker compose up` starts all services healthy. Flyway runs clean.

| Deliverable | Status |
|---|---|
| `aether-infra/docker/docker-compose.yml` — 7 services with `condition: service_healthy` dependency chains | ✅ |
| Services: pgvector/pgvector:pg16, redis:7-alpine, cp-zookeeper:7.7.1, cp-kafka:7.7.1, ollama/ollama:latest, prometheus:v2.55.0, grafana:11.3.0 | ✅ |
| `aether-infra/docker/prometheus.yml` — scrapes host.docker.internal:8080 and :8081 | ✅ |
| `aether-infra/docker/.env.example` — all environment variable names (no values) | ✅ |
| `V001__create_tenants.sql` | ✅ |
| `V002__create_endpoints.sql` | ✅ |
| `V003__create_api_calls.sql` | ✅ |
| `V004__create_memory_embeddings.sql` — `vector(384)` column | ✅ |
| `V005__create_policies.sql` | ✅ |
| `V006__create_policy_versions.sql` | ✅ |
| `V007__create_agent_decisions.sql` | ✅ |
| `V008__create_audit_log.sql` — no FK constraints by design (survives entity deletion) | ✅ |
| `V009__create_outbox_events.sql` — partial index on `published = false` | ✅ |
| pgvector IVFFlat index on `memory_embeddings.embedding` (lists=100, cosine ops) | ✅ |

**Commit:** `infra: add docker compose stack and flyway migrations`

**Verification:** `docker compose config --quiet` — valid stack. All migrations forward-only, no destructive DDL.

---

## Phase 4 — Core Domain Model + LLM Abstraction ✅

**Goal:** `aether-domain` compiles with domain model. Multi-provider LLM abstraction operational. Unit tests pass at ≥80% line coverage.

| Deliverable | Status |
|---|---|
| `ApiCall` aggregate — immutable, raises `ApiCallRecordedEvent` on creation | ✅ |
| `Tenant` aggregate — lifecycle state machine (ACTIVE → SUSPENDED / DEPROVISIONED) | ✅ |
| Value objects (Java 21 records): `ApiCallId`, `TenantId`, `ApiEndpoint`, `CallMetrics`, `MemoryRecord` | ✅ |
| Enums: `CallOutcome`, `HttpMethod`, `MemoryType` (EPISODIC/SEMANTIC/PROCEDURAL/EMOTIONAL), `TenantStatus` | ✅ |
| Sealed `DomainEvent` interface (`permits ApiCallRecordedEvent, PolicyViolatedEvent, AgentDecisionEvent, GovernanceUpdatedEvent`) | ✅ |
| Port interfaces: `EventPublisher`, `ApiCallRepository`, `TenantRepository`, `MemoryStore`, `EmbeddingPort`, `PolicyRepository` | ✅ |
| Exception hierarchy: `AetherException` (abstract), `TenantNotFoundException`, `PolicyViolationException`, `AgentException` | ✅ |
| `LlmClient` interface + `LlmRequest` / `LlmResponse` records + `LlmProvider` enum | ✅ |
| `OllamaLlmClient` — local Ollama `/api/chat` (Gemma2:2b, Phi-3-mini) | ✅ |
| `GroqLlmClient` — Groq cloud inference (Llama-3.3-70b, Mixtral-8x7b) | ✅ |
| `AnthropicLlmClient` — Claude models (claude-haiku-4-5, claude-sonnet-4-6) | ✅ |
| `LlmClientConfig` — `@ConditionalOnProperty(name = "aether.llm.provider")`, Ollama default | ✅ |
| Agent SPI: `Agent` interface, `AgentCapability`, `AgentInput`, `AgentOutput`, `AgentDecision` | ✅ |
| Confidence gate: BLOCK + confidence < 0.8 → `autoEnforced=false` → human-in-the-loop | ✅ |
| `AgentRegistry` — Spring `List<Agent>` injection, `disableAgent()` kill-switch | ✅ |
| Domain unit tests (19 tests, 0 failures) | ✅ |

**Commit:** `feat(core): implement domain model, events, ports, and multi-provider LLM abstraction`

**Verification:** `mvn test -pl aether-domain` — 19 tests, 0 failures.

---

## Phase 5 — Proxy Layer ✅

**Goal:** Requests proxied through Spring Cloud Gateway are captured and published as Kafka events.

| Deliverable | Status |
|---|---|
| `TenantAuthFilter` (order=-100) — X-API-Key → SHA-256 → tenant lookup → 401 for unknown/suspended; actuator bypass | ✅ |
| `RedactionFilter` (order=-90) — strips Authorization, X-API-Key, Cookie, Set-Cookie, X-Client-Secret downstream | ✅ |
| `ApiCallCaptureFilter` (order=-50) — fire-and-forget capture via `boundedElastic` scheduler after response completes | ✅ |
| `JdbcTenantRepository` — `NamedParameterJdbcTemplate`, `ON CONFLICT DO UPDATE` upsert | ✅ |
| `JdbcOutboxRepository` — writes JSONB payload; `markPublished` bulk-updates via `ANY(:ids::uuid[])` | ✅ |
| `OutboxRelayScheduler` — `@Scheduled` every 5s; reads up to 100 unpublished events; publishes to Kafka; bulk-marks published | ✅ |
| `TenantKeyResolver` — per-tenant Redis rate limiting key; falls back to IP for unauthenticated requests | ✅ |
| `RequestRateLimiter` — 100 rps / 200 burst per tenant via Redis | ✅ |
| `CircuitBreaker` — Resilience4j, 50% failure threshold, 30s open duration | ✅ |
| Kafka producer: `acks=all`, idempotent | ✅ |
| Unit tests (5 tests, 0 failures): TenantAuthFilter — missing key, unknown key, suspended, valid, actuator bypass | ✅ |

**Commit:** `feat(proxy): implement gateway filters, outbox relay, and Redis rate limiting`

**Verification:** `mvn test -pl aether-proxy` — 5 tests, 0 failures.

---

## Phase 6 — Memory Layer ✅

**Goal:** `MemoryStore` stores and retrieves semantically similar calls via pgvector cosine similarity.

| Deliverable | Status |
|---|---|
| `OllamaEmbeddingService` — implements `EmbeddingPort`; calls `/api/embed` (all-MiniLM-L6-v2, 384-dim); validates dimension | ✅ |
| `PGVectorMemoryStore` — cosine distance via `<=>` operator; float[] ↔ `[x,y,z]` string for `::vector` cast | ✅ |
| `findSimilar()` — reinforces memory strength +5% on access | ✅ |
| `MemoryLifecycleService` — daily 5% strength decay on records idle > 7 days; weekly purge below 0.05 | ✅ |
| `ApiCallMemoryConsumer` — Kafka listener on `aether.api.calls`; classifies PROCEDURAL=200, SEMANTIC=4xx, EPISODIC=5xx/timeout | ✅ |
| Embedding failures skip gracefully (no exception propagation) | ✅ |
| `MemoryConfig` — `@Configuration` wiring with constructor injection | ✅ |
| Unit tests (9 tests, 0 failures): round-trip serialization, 384-dim validation, memory type classification, failure skip | ✅ |

**Commit:** `feat(memory): implement embedding service, pgvector store, lifecycle, and Kafka consumer`

**Verification:** `mvn test -pl aether-memory` — 9 tests, 0 failures.

---

## Phase 7 — Agent Subsystem ✅

**Goal:** AgentOrchestrator, AgentRegistry, GovernanceAgent, RetryAgent, and HallucinationDetectorAgent operational.

| Deliverable | Status |
|---|---|
| `AgentOrchestrator` — dispatches by capability; `MAX_ITERATIONS=5` guard | ✅ |
| `orchestrateParallel()` — `VirtualThreadPerTaskExecutor` + `CompletableFuture.allOf()` | ✅ |
| `OrchestrationResult` — `requiresHumanReview()`, `hasAutoBlock()`, `highestSeverityDecision()` (BLOCK > ALERT > SUGGEST > DEFER > ALLOW) | ✅ |
| `GovernanceAgent` — LLM JSON response protocol; `decision`/`confidence`/`rationale`; defaults ALLOW on parse error | ✅ |
| `RetryAgent` — counts failure memories; fast-path for zero-failure calls; suggests exponential backoff strategy | ✅ |
| `HallucinationDetectorAgent` — validates LLM outputs against memory patterns; defaults ALERT when LLM unavailable | ✅ |
| `AgentRegistry` — Spring `List<Agent>` injection; `disableAgent()` kill-switch | ✅ |
| `AgentsConfig` — `@Configuration` wiring via constructor injection | ✅ |
| Unit tests (14 tests, 0 failures): orchestrator dispatch, no-agent, human review, auto-block, registry capability lookup, kill-switch, governance parse/fallback | ✅ |

**Commit:** `feat(agents): implement orchestrator, governance, retry, and hallucination detector agents`

**Verification:** `mvn test -pl aether-agents` — 14 tests, 0 failures.

---

## Phase 8 — Policy Engine ✅

**Goal:** YAML policies stored versioned in PostgreSQL, evaluated via SpEL at runtime, full audit trail.

| Deliverable | Status |
|---|---|
| `PolicyRule` record — name, SpEL condition, `PolicyAction` enum, priority | ✅ |
| `PolicyEvaluationContext` record — method, path, responseCode, latencyMs, outcome, tenantId, headers | ✅ |
| `PolicyEvaluationResult` record — `overallAction`, matched `RuleMatch` list, `isBlocked()`, `hasAlerts()` | ✅ |
| `SpelPolicyEngine` — `SimpleEvaluationContext` (read-only, no arbitrary execution); severity ordering BLOCK > RATE_LIMIT > ALERT > AUDIT > ALLOW; skips malformed expressions | ✅ |
| `JdbcPolicyRepository` — single-active-per-tenant invariant (supersedes previous before activating new); auto-versioning via `MAX(version) + 1` | ✅ |
| `GdprRedactionService` — regex redaction for email, E.164 phone, Visa/MC/Amex, SSN, JWT Bearer, API keys | ✅ |
| `AuditLogService` — JSONB audit log; no FK constraints (survives entity deletion) | ✅ |
| `PolicyConfig` — `@Configuration` wiring | ✅ |
| Unit tests (14 tests, 0 failures): no-policy allow, latency block, error alert, priority ordering, malformed YAML safety, GDPR multi-PII, null safety | ✅ |

**Commit:** `feat(policy): implement SpEL policy engine, GDPR redaction, audit log, and policy store`

**Verification:** `mvn test -pl aether-policy` — 14 tests, 0 failures.

---

## Phase 9 — Admin REST API ✅

**Goal:** Full REST API for tenant/policy/memory management. OpenAPI documentation live.

| Deliverable | Status |
|---|---|
| `TenantController` — POST /api/v1/tenants (SHA-256 key hash on onboard); GET /{id}; PUT /{id}/suspend; PUT /{id}/reactivate | ✅ |
| `PolicyController` — POST /api/v1/tenants/{tid}/policies; PUT /{pid}/activate; PUT /{pid}/archive; GET /active | ✅ |
| `MemoryController` — POST /search (embed query → cosine similarity); DELETE /{memoryId} | ✅ |
| `GlobalExceptionHandler` — RFC 7807 `ProblemDetail`; TenantNotFoundException (404), AetherException (500), validation (400), IllegalArgumentException (400) | ✅ |
| `SecurityConfig` — stateless JWT OAuth2 resource server; actuator + Swagger UI open; all `/api/**` authenticated | ✅ |
| `ApiConfig` — `@Configuration`; `JdbcApiTenantRepository` adapter with UPSERT | ✅ |
| `AetherApiApplication` — `scanBasePackages = "com.suplab.aether"` picks up MemoryConfig, PolicyConfig, AgentsConfig | ✅ |
| Swagger UI via springdoc-openapi 2.6.0 at `/api/swagger-ui.html` | ✅ |
| Parent POM — `-parameters` compiler flag added for Spring MVC path variable resolution | ✅ |
| MockMvc tests (17 tests, 0 failures): TenantController (7), PolicyController (6), MemoryController (4) | ✅ |

**Commit:** `feat(api): implement admin REST API — tenant, policy, memory controllers`

**Verification:** `mvn clean test -pl aether-api` — 17 tests, 0 failures.

---

## Phase 10 — Advanced Agents + Observability ✅

**Goal:** Temporal prediction, reflection agents, and Micrometer metrics operational.

| Deliverable | Status |
|---|---|
| `TemporalPredictionAgent` — analyses EPISODIC/SEMANTIC memory counts; LLM-powered ALERT/DEFER predictions; fast-path DEFER for zero memories | ✅ |
| `ReflectionAgent` — computes procedural health score (`proceduralCount / (total + 1)`); fast-path ALLOW when health ≥ 0.5 (no LLM); LLM SUGGEST/DEFER when poor | ✅ |
| `AgentOrchestrator` — Micrometer `aether.agent.executions` counter (agent, decision tags) + `aether.agent.latency` timer (agent tag) | ✅ |
| `AgentsConfig` — wires `TemporalPredictionAgent`, `ReflectionAgent`; passes `MeterRegistry` to orchestrator | ✅ |
| `aether-agents/pom.xml` — `micrometer-core` compile + `micrometer-test` test dependencies added | ✅ |
| Unit tests (26 total, 0 failures): TemporalPredictionAgent (6), ReflectionAgent (6), all prior tests continue passing | ✅ |

**Commit:** `feat(agents): add temporal prediction, reflection agents, and Micrometer metrics`

**Verification:** `mvn clean test -pl aether-agents` — 26 tests, 0 failures.

---

## Phase 11 — Multi-Tenancy + Compliance ✅

**Goal:** Full tenant data isolation. GDPR-compliant data subject access and erasure.

| Deliverable | Status |
|---|---|
| `V010__tenant_gdpr_preferences.sql` — `memory_opt_out` and `data_retention_days` columns on `tenants` | ✅ |
| `V011__row_level_security.sql` — PG RLS enabled on `memory_embeddings`, `api_calls`, `policies`, `agent_decisions`, `audit_log` | ✅ |
| `JdbcTenantRepository` updated — `memory_opt_out` included in SELECT and UPSERT SQL | ✅ |
| `ApiCallMemoryConsumer` — skips embedding when tenant has opted out; GDPR redaction before embedding | ✅ |
| `TenantController` — `PUT /{id}/gdpr/memory-opt-out`, `DELETE /{id}/gdpr/memory-opt-out`, `DELETE /{id}/memories` (right-to-erasure) | ✅ |
| `AuditController` — `GET /api/v1/tenants/{tenantId}/audit?limit=50` paged audit log | ✅ |
| `AuditLogService` — `findByTenant()` query + audit events wired to all lifecycle transitions | ✅ |
| `TenantResponse` record — `memoryOptOut` field added | ✅ |
| `PolicyController` — `AuditLogService` wired; `POLICY_CREATED`, `POLICY_ACTIVATED`, `POLICY_ARCHIVED` events | ✅ |
| Tests: `ApiCallMemoryConsumerTest` (12), `TenantControllerTest` (11), `AuditControllerTest` (3) | ✅ |

**Commit:** `feat(compliance): GDPR memory opt-out, right-to-erasure, RLS, and audit logging`

**Verification:** `mvn clean test -pl aether-memory,aether-api,aether-proxy,aether-policy` — 55 tests, 0 failures.

---

## Phase 12 — CI/CD + Kubernetes ✅

**Goal:** Every push triggers a full quality gate. Kubernetes manifests deploy both services with production-grade security posture.

| Deliverable | Status |
|---|---|
| `.github/workflows/ci.yml` — Temurin 21, Maven verify, PostgreSQL service container, JaCoCo + Surefire report upload | ✅ |
| `.github/workflows/quality-gate.yml` — Checkstyle (google_checks.xml) + OWASP dependency-check (failBuildOnCVSS=9) on PRs to main | ✅ |
| `.github/owasp-suppressions.xml` — OWASP suppression file for accepted false positives | ✅ |
| `pom.xml` — `maven-checkstyle-plugin:3.6.0` added to pluginManagement | ✅ |
| `aether-infra/k8s/namespace.yaml` — `aether-grid` namespace | ✅ |
| `aether-infra/k8s/aether-api/` — Deployment (2 replicas, non-root securityContext, liveness/readiness probes, resource limits), Service (ClusterIP 8081), HPA (min 2 / max 8, CPU 70%), ConfigMap | ✅ |
| `aether-infra/k8s/aether-proxy/` — Deployment (2 replicas, same security posture), Service (ClusterIP 8080), HPA (min 2 / max 16), ConfigMap | ✅ |
| `aether-infra/k8s/secrets-template.yaml` — documents all required Secret keys without values | ✅ |

**Commit:** `build(ci): add GitHub Actions CI/CD pipelines and Kubernetes manifests`

**Verification:** PR to main → `quality-gate` workflow green (Checkstyle + OWASP). Push to any branch → `ci` workflow green (build + test + coverage). `kubectl apply -f aether-infra/k8s/` → all resources created in `aether-grid` namespace.

---

## Phase 13 — Self-Improving Agents ✅

**Goal:** Agents record decision outcomes and learn from them. A scheduled service reviews feedback weekly and generates improvement suggestions via LLM.

| Deliverable | Status |
|---|---|
| `DecisionOutcome` enum: `CORRECT`, `INCORRECT`, `PARTIALLY_CORRECT`, `UNKNOWN` | ✅ |
| `AgentFeedback` record: id, tenantId, agentType, decisionId, originalDecision, originalConfidence, outcome, outcomeDetail, recordedAt | ✅ |
| `AgentFeedbackPort` interface: `record()`, `findByAgentType()`, `getPerformanceStats()` | ✅ |
| `SelfImprovingAgent` — meta-agent that analyses feedback history, invokes LLM for suggestions | ✅ |
| `SELF_IMPROVEMENT` capability added to `AgentCapability` enum | ✅ |
| `AgentLearningService` — `@Scheduled` weekly review across all tenants | ✅ |
| `LearningConfig` — `@Configuration` wiring for the learning service | ✅ |
| `AgentController` — `POST /api/v1/tenants/{tenantId}/agents/feedback` + `GET /api/v1/tenants/{tenantId}/agents/performance` | ✅ |
| `FeedbackRequest` DTO | ✅ |
| `JdbcAgentFeedbackRepository` registered in `ApiConfig` | ✅ |
| `V012__agent_feedback.sql` — `agent_feedback` table with RLS policy and `(tenant_id, agent_type)` index | ✅ |

**Commit:** `feat(agents): add feedback loop, self-improving agent, and weekly learning service`

---

## Phase 14 — Dashboard / Control Center ✅

**Goal:** Operators have a self-contained web dashboard providing real-time visibility into system health, agent activity, memory distribution, and recent decisions without requiring authentication.

| Deliverable | Status |
|---|---|
| `DashboardStatsService` — queries tenants, memory_embeddings, policies, agent_decisions, audit_log | ✅ |
| `GET /dashboard/stats` — system stats snapshot | ✅ |
| `GET /dashboard/decisions?limit=20` — recent agent decisions | ✅ |
| `GET /dashboard/memory-breakdown` — memory type counts and avg strength | ✅ |
| `GET /dashboard/agent-breakdown` — per-agent decision counts last 7 days | ✅ |
| `GET /dashboard/agents` — registered agent type list via `AgentRegistry.registeredTypes()` | ✅ |
| `GET /dashboard/stream` — SSE live stream (single snapshot; client reconnects every 10s) | ✅ |
| `dashboard.html` — self-contained dark-theme SPA (stat cards, tables, SSE panel, 10s auto-refresh) | ✅ |
| `SecurityConfig` updated — `/dashboard/**` and `/*.html` permitted without auth | ✅ |
| `AgentRegistry` updated — `registeredTypes()` method added | ✅ |

**Commit:** `feat(api): add dashboard stats service, SSE stream, and self-contained dashboard SPA`

---

## Phase 15 — Kubernetes + Helm Production Hardening ✅

**Goal:** A single `helm install` deploys the full stack on vanilla Kubernetes, AWS EKS, or OpenShift with zero manual manifest editing.

| Deliverable | Status |
|---|---|
| `aether-infra/helm/aether-grid/values.yaml` — cloud-agnostic defaults (GHCR, nginx ingress, 2 replicas) | ✅ |
| `values-aws.yaml` — EKS overrides: ALB Ingress Controller, IRSA serviceAccount annotations, ECR registry, ExternalDNS, ServiceMonitor | ✅ |
| `values-openshift.yaml` — OCP overrides: `openshift.enabled: true`, Quay.io, Route (edge TLS), ServiceMonitor | ✅ |
| Helm templates: namespace, aether-api (deployment/service/hpa/configmap/serviceaccount), aether-proxy (same), ingress, route, servicemonitor, _helpers.tpl, NOTES.txt | ✅ |
| OpenShift-aware securityContext: omits `runAsUser`/`fsGroup` when `openshift.enabled` | ✅ |
| `startupProbe` on both Deployments | ✅ |
| `checksum/config` rolling annotation (ConfigMap changes trigger pod restarts) | ✅ |
| `automountServiceAccountToken: false` on all ServiceAccounts | ✅ |
| `aether-api/Dockerfile` — multi-stage (eclipse-temurin:21-jdk-noble → :21-jre-noble), non-root uid 1000, `--enable-preview`, `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`, liveness HEALTHCHECK | ✅ |
| `aether-proxy/Dockerfile` — same pattern as aether-api | ✅ |
| `.dockerignore` at repo root | ✅ |
| `.github/workflows/docker-build.yml` — OIDC-only, matrix [aether-api, aether-proxy], linux/amd64+arm64, GHCR push on main/release/** | ✅ |
| `.github/workflows/helm-release.yml` — helm lint (all 3 values files) + template dry-run + OCI push to ghcr.io/suplab/helm on release/** | ✅ |

**Commit:** `build(infra): add Helm chart, multi-stage Dockerfiles, and container CI/CD workflows`

**Verification:** `helm lint aether-infra/helm/aether-grid -f values-aws.yaml -f values-openshift.yaml` — clean. `helm template` dry-run against all three values files produces valid YAML. Docker build matrix green on push to main.

---

## Phase 16 — Aether Core Integration ✅

**Goal:** Aether Grid integrates with [Aether Core](https://github.com/suplab/aether-core) (`suplab/aether-core`) to enrich agent decisions with personal user context.

| Deliverable | Status |
|---|---|
| `PersonalContextPort` interface in `aether-domain` | ✅ |
| `AetherCoreHttpAdapter` — fetches personal context from Core's REST API | ✅ |
| `AetherCoreBridgeAgent` — enriches `AgentInput.context` with memories, preferences, emotional state | ✅ |
| `aether.core.base-url` configuration property for Core endpoint | ✅ |
| Integration with `GET /api/v1/personal-context/{tenantId}/{userId}` (Core API) | ✅ |

---

## Phase 17 — Aether Core Scaffold (sister repo bootstrap) ✅

**Goal:** Bootstrap the `suplab/aether-core` sister repository with its own EEIK governance layer, Maven structure, and domain scaffold.

**Status:** Complete. `suplab/aether-core` is bootstrapped and has matured well beyond a scaffold — its own EEIK governance layer, Maven multi-module structure (`core-domain` / `core-memory` / `core-api` / `core-infra`), a personal-memory + reasoning-session domain over pgvector, GDPR erasure + retention + data-portability, and a production Helm chart. With this, Grid's roadmap (Phases 0–17) is fully delivered; new capability now lands in the sister platforms (Core, Memory, Vault, Flow).

> For deliverables and status, see [suplab/aether-core](https://github.com/suplab/aether-core).

---

## Future Considerations

These are tracked but not scoped for the current roadmap:

- **Dynamic agent creation** — spawning new agents at runtime for novel task types
- **Agent marketplace** — catalog of shareable, versioned agent capabilities
- **Federated memory** — memory shared across organizational boundaries with consent controls
- **Edge intelligence** — lightweight agent runtime for IoT / embedded devices

---

*Last updated: Phase 16 — Aether Core Integration*
*See [Progress](progress.md) for live status · [Architecture](architecture.md) for technical detail*

---

> **Aether Core Roadmap:** For the personal cognitive engine roadmap, see [suplab/aether-core/docs/roadmap.md](https://github.com/suplab/aether-core/blob/main/docs/roadmap.md).

---

## Ecosystem review — future backlog

> Repo-specific items from the [ecosystem improvement backlog](https://github.com/doubts-suplab/aether/blob/main/docs/roadmaps/ecosystem-improvements.md). Planned, not started.
> Feasibility: **S** small · **M** moderate · **L** large. License unchanged (AGPL-3.0).

| Item | Feasibility |
|---|---|
| Deeper integration with Memory / Vault / Flow | M–L |
| More sophisticated agent orchestration + evaluation | M–L |
| Proxy latency/throughput under realistic load | M |
| Stronger hallucination/reflection loops with measurable improvement | L |
| Expand agent SPI + marketplace-like extensibility | M–L |
| Production-harden the confidence gate + DEFER path | M |
