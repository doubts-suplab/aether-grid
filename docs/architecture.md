# Aether — Technical Architecture

> Living document. Updated with each phase completion. Last updated: Phase 15.

---

## Overview

AetherGrid is implemented as a **Maven multi-module, modular-monolith** — two deployable Spring Boot applications backed by six library modules. Module boundaries enforce DDD bounded contexts at compile time. Future service extraction is possible without architectural surgery because ports and adapters are already separated.

**Two runnable applications:**
- `aether-proxy` — Data Plane (port 8080): Spring Cloud Gateway intercepting all API traffic
- `aether-api` — Control Plane (port 8081): Admin REST API for governance and configuration

**Five library modules** (JAR, no `main` class):
- `aether-domain` — shared domain model, events, port interfaces (pure Java, no Spring dependency)
- `aether-memory` — embedding and vector storage
- `aether-agents` — agent SPI, registry, orchestrator, all agent implementations, multi-provider LLM abstraction
- `aether-policy` — policy engine, rule evaluation, GDPR redaction, audit log
- `aether-infra` — Docker Compose, Flyway migrations, K8s manifests (no Java)

---

## Module Dependency Graph

```
aether-proxy  ──────────────────────────────────────────────┐
      │                                                      │
      ▼                                                      ▼
aether-domain ◄── aether-memory ◄── aether-agents ──► aether-policy
                                                           │
                                                      aether-api
```

`aether-domain` has **no dependencies** on other modules or on Spring. All other modules depend on it. `aether-proxy` and `aether-api` depend on the full stack.

---

## Core Design Patterns

### Hexagonal Architecture (Ports & Adapters)

All outbound I/O (database, Kafka, Ollama, Redis) is behind interfaces defined in `aether-domain`:

```java
// aether-domain: port (interface only, no implementation)
public interface MemoryStore {
    void store(MemoryRecord record);
    List<MemoryRecord> findSimilar(float[] queryVector, int topK, TenantId tenantId);
    List<MemoryRecord> findByType(MemoryType type, TenantId tenantId);
    void delete(UUID memoryId, TenantId tenantId);
}

// aether-memory: adapter (implements the port)
@Component
public class PGVectorMemoryStore implements MemoryStore { ... }
```

Tests use in-memory stubs. The proxy has no knowledge of whether storage is pgvector or Chroma.

### Domain Events (Sealed Hierarchy)

All cross-module communication goes through sealed domain events on the Kafka bus:

```java
public sealed interface DomainEvent
    permits ApiCallRecordedEvent, PolicyViolatedEvent,
            AgentDecisionEvent, GovernanceUpdatedEvent { }
```

Java 21 pattern matching enables exhaustive handling — the compiler enforces that all event types are handled.

### Agent Plugin Pattern (SPI)

Agents are registered via Spring's constructor-injected `List<Agent>`:

```java
@Component
public class AgentRegistry {
    private final List<Agent> agents; // Spring injects all @Component Agent impls

    public AgentRegistry(List<Agent> agents) {
        this.agents = agents;
    }

    public List<Agent> findByCapability(AgentCapability capability) {
        return agents.stream()
            .filter(a -> a.canHandle(capability))
            .toList();
    }
}
```

Adding a new agent = implement `Agent` + `@Component`. Zero configuration.

### Transactional Outbox (Reliable Event Publishing)

Direct `KafkaTemplate.send()` inside a transaction creates dual-write risk. AetherGrid uses the outbox pattern:

```
DB Transaction:
  INSERT INTO api_calls ...
  INSERT INTO outbox_events (event_type, payload, published=false)
  COMMIT

OutboxRelayScheduler (@Scheduled every 5s):
  SELECT id, event_type, payload FROM outbox_events WHERE published = false LIMIT 100
  KafkaTemplate.send(...).get()
  UPDATE outbox_events SET published=true WHERE id = ANY(:ids::uuid[])
```

Ensures events are never lost even if Kafka is temporarily unavailable.

### Policy-as-Code (SpEL in YAML)

Governance rules are stored as YAML in PostgreSQL. Rules use Spring EL expressions evaluated at runtime via `SimpleEvaluationContext` (read-only — no arbitrary Java execution):

```yaml
# Example policy stored in policies table
id: tenant-a-latency-policy
version: 3
rules:
  - name: high-latency-alert
    condition: "#call.latencyMs > 2000 && #call.outcome == 'FAILURE'"
    action: ALERT
    priority: 100
  - name: block-excessive-errors
    condition: "#call.responseCode >= 500"
    action: BLOCK
    priority: 200
```

No redeployment needed to change governance rules.

### Multi-Provider LLM Abstraction

All agent LLM calls go through the `LlmClient` interface. The active provider is selected at startup via `@ConditionalOnProperty(name = "aether.llm.provider")`:

```java
public interface LlmClient {
    LlmResponse complete(LlmRequest request);
    LlmProvider provider();
    boolean isAvailable();
}
```

Three production adapters are implemented:

| Provider | Adapter | Models |
|---|---|---|
| Ollama (default) | `OllamaLlmClient` | Gemma2:2b, Phi-3-mini, any local model |
| Groq cloud | `GroqLlmClient` | Llama-3.3-70b, Mixtral-8x7b, Gemma2-9b |
| Anthropic | `AnthropicLlmClient` | claude-haiku-4-5, claude-sonnet-4-6 |

Swapping providers requires only an env var change (`AETHER_LLM_PROVIDER`). No code changes.

---

## Proxy Filter Chain

Inbound requests traverse the following ordered filter chain in `aether-proxy`:

```
Inbound Request
       │
       ▼
TenantAuthFilter (order = -100)
  X-API-Key header → SHA-256 hash → lookup in tenants table
  401 for unknown key or SUSPENDED tenant
  Actuator paths (/actuator/**) bypass auth entirely
  Stores TenantContext in exchange attributes
       │
       ▼
RedactionFilter (order = -90)
  Strips from downstream request: Authorization, X-API-Key,
  Cookie, Set-Cookie, X-Client-Secret
  Prevents credential leakage to proxied APIs
       │
       ▼
RequestRateLimiter (Spring Cloud Gateway built-in)
  Redis token bucket: 100 rps / 200 burst per tenant
  Key resolved by TenantKeyResolver (falls back to IP)
       │
       ▼
Spring Cloud Gateway Route
  Resolves target URL from endpoints table
  CircuitBreaker (Resilience4j): 50% failure threshold, 30s open
       │
       ▼
Downstream API
       │
       ▼
ApiCallCaptureFilter (order = -50, doFinally hook)
  Captures response status + latency after chain completes
  Serialises ApiCallRecordedEvent as JSON into outbox_events
  Fire-and-forget via Schedulers.boundedElastic()
       │
       ▼
OutboxRelayScheduler (@Scheduled every 5s)
  Reads up to 100 unpublished events
  Publishes to Kafka topic: aether.api.calls
  Bulk-marks published via ANY(:ids::uuid[])
```

---

## Data Model

### Core Tables (Flyway migrations V001–V012)

```
tenants
  id UUID PK
  name VARCHAR
  api_key_hash VARCHAR       -- SHA-256 of the raw key; raw key never stored
  status VARCHAR             -- ACTIVE | SUSPENDED | DEPROVISIONED
  created_at TIMESTAMP

endpoints
  id UUID PK
  tenant_id UUID FK → tenants.id
  name VARCHAR
  base_url VARCHAR
  path_pattern VARCHAR
  active BOOLEAN

api_calls
  id UUID PK
  tenant_id UUID FK → tenants.id
  endpoint_id UUID FK → endpoints.id
  method VARCHAR
  request_hash VARCHAR
  response_code INT
  latency_ms BIGINT
  outcome VARCHAR            -- SUCCESS | FAILURE | TIMEOUT
  captured_at TIMESTAMP

memory_embeddings
  id UUID PK
  tenant_id UUID FK
  memory_type VARCHAR        -- EPISODIC | SEMANTIC | PROCEDURAL | EMOTIONAL
  content TEXT
  embedding vector(384)      -- pgvector column, all-MiniLM-L6-v2 output
  strength FLOAT             -- 0.0–1.0, reinforced +5% on retrieval, decays daily
  last_accessed TIMESTAMP
  created_at TIMESTAMP

policies
  id UUID PK
  tenant_id UUID FK
  status VARCHAR             -- DRAFT | ACTIVE | SUPERSEDED
  yaml_content TEXT
  created_at TIMESTAMP
  activated_at TIMESTAMP

policy_versions
  id UUID PK
  policy_id UUID FK → policies.id
  version INT                -- auto-incremented: MAX(version) + 1
  yaml_content TEXT
  changed_by VARCHAR
  changed_at TIMESTAMP

agent_decisions
  id UUID PK
  call_id UUID FK → api_calls.id
  agent_type VARCHAR
  capability VARCHAR
  decision VARCHAR           -- ALLOW | BLOCK | ALERT | SUGGEST | DEFER
  confidence FLOAT
  auto_enforced BOOLEAN      -- false unless BLOCK with confidence >= 0.95 (agent-harness gate)
  rationale TEXT
  decided_at TIMESTAMP

audit_log
  id UUID PK
  tenant_id UUID
  entity_type VARCHAR
  entity_id UUID
  action VARCHAR
  actor VARCHAR
  detail JSONB
  occurred_at TIMESTAMP
  -- No FK constraints: audit records survive entity deletion

outbox_events
  id UUID PK
  event_type VARCHAR
  payload JSONB
  published BOOLEAN DEFAULT false
  created_at TIMESTAMP
  published_at TIMESTAMP
  -- Partial index on (published = false) for efficient relay queries

agent_feedback                        -- V012
  id UUID PK
  tenant_id UUID
  agent_type VARCHAR
  decision_id UUID
  original_decision VARCHAR           -- ALLOW | BLOCK | ALERT | SUGGEST | DEFER
  original_confidence FLOAT
  outcome VARCHAR                     -- CORRECT | INCORRECT | PARTIALLY_CORRECT | UNKNOWN
  outcome_detail TEXT
  recorded_at TIMESTAMP
  -- RLS policy: current_setting('app.tenant_id', true)
  -- Index on (tenant_id, agent_type) for performance stats queries
```

### Vector Index

```sql
-- Cosine similarity index on memory_embeddings.embedding
CREATE INDEX ON memory_embeddings USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);
```

---

## Memory Lifecycle

```
ApiCallRecordedEvent arrives via Kafka (topic: aether.api.calls)
       │
       ▼
ApiCallMemoryConsumer (@KafkaListener)
  Parses JSON payload
  Classifies memory type:
    HTTP 2xx  → PROCEDURAL (successful process)
    HTTP 4xx  → SEMANTIC   (API contract facts)
    HTTP 5xx / timeout → EPISODIC (failure experience)
       │
       ▼
OllamaEmbeddingService.embed(content)
  POST /api/embed → Ollama (all-MiniLM-L6-v2)
  Returns float[384]
  Validates dimension — throws EmbeddingException on mismatch
  Embedding failures skip gracefully (no exception propagation)
       │
       ▼
PGVectorMemoryStore.store(record)
  float[] → "[x,y,z,...]" string → ::vector cast
  INSERT INTO memory_embeddings ... ON CONFLICT DO UPDATE
       │
Later: Agent semantic lookup
       │
       ▼
PGVectorMemoryStore.findSimilar(queryVector, topK, tenantId)
  SELECT id, content, memory_type, strength, ...
  FROM memory_embeddings
  WHERE tenant_id = :tenantId
  ORDER BY embedding <=> :queryVector::vector
  LIMIT :topK
  Reinforces strength by +5% on each retrieval
       │
       ▼
Returns List<MemoryRecord> to calling agent
  Agent builds LLM prompt: context + top-K memories + active policy
```

### Memory Strength Lifecycle

```
On store:     strength = 1.0 (initial)
On retrieval: strength = MIN(strength * 1.05, 1.0)
Daily decay:  strength = strength * 0.95  (records idle > 7 days)
Weekly purge: DELETE WHERE strength < 0.05
```

---

## Agent Lifecycle

```
AgentInput created (ApiCall + relevant memories + active policy)
       │
       ▼
AgentOrchestrator.orchestrate(input)
  Dispatches to all agents matching the required AgentCapability
  MAX_ITERATIONS = 5 guard (prevents infinite loops)
       │
       ▼
For each agent (parallel via VirtualThreadPerTaskExecutor):
  agent.process(input) → AgentOutput
  Micrometer: aether.agent.executions counter (agent, decision tags)
  Micrometer: aether.agent.latency timer (agent tag)

  Confidence gate (centralized in the agent-harness ConfidenceGate; single source of truth):
    A BLOCK auto-enforces only at confidence >= 0.95 (AIEL authority ladder), else:
      autoEnforced = false  → human-in-the-loop required
      Decision is recorded but NOT automatically enforced
    GovernanceAgent runs through Harness.invoke; AgentOutput delegates the same gate.
    (Supersedes Grid's former flat 0.8 literal; the gate lives in ONE place — see HarnessConfidenceGate.)

  Persist AgentDecision to agent_decisions table
  Publish AgentDecisionEvent to Kafka
       │
       ▼
OrchestrationResult returned
  requiresHumanReview():  any BLOCK with autoEnforced=false
  hasAutoBlock():         any BLOCK with autoEnforced=true
  highestSeverityDecision(): BLOCK > ALERT > SUGGEST > DEFER > ALLOW
```

### Agent Inventory

| Agent | Capability | Phase | Behaviour |
|---|---|---|---|
| `GovernanceAgent` | `GOVERNANCE` | 7 | LLM JSON response protocol; ALLOW/BLOCK/ALERT; defaults ALLOW on parse error |
| `RetryAgent` | `RETRY_OPTIMIZATION` | 7 | Counts failure memories; fast-path for zero failures; suggests exponential backoff |
| `HallucinationDetectorAgent` | `HALLUCINATION_DETECTION` | 7 | Validates LLM outputs against memory patterns; defaults ALERT when LLM unavailable |
| `TemporalPredictionAgent` | `TEMPORAL_PREDICTION` | 10 | Analyses EPISODIC/SEMANTIC memory counts; fast-path DEFER for zero memories; LLM ALERT/DEFER |
| `ReflectionAgent` | `REFLECTION` | 10 | Health score = `proceduralCount / (total + 1)`; fast-path ALLOW when ≥ 0.5; LLM SUGGEST/DEFER |
| `SelfImprovingAgent` | `SELF_IMPROVEMENT` | 13 | Reads `AgentFeedback` history; builds LLM prompt with outcome stats; returns improvement suggestions as SUGGEST decisions |

---

## Security

| Concern | Implementation |
|---|---|
| Authentication (services) | `X-API-Key` header — SHA-256 hash compared against `tenants.api_key_hash`; raw key never stored |
| Authentication (humans) | JWT via Spring Security 6 OAuth2 Resource Server (stateless) |
| Multi-tenant data isolation | All queries include `WHERE tenant_id = :tenantId`; PG row-level security planned (Phase 11) |
| Secrets management | Environment variables (local); never committed to source |
| GDPR | `GdprRedactionService` strips email, E.164 phone, Visa/MC/Amex, SSN, JWT Bearer, API keys before any persistence |
| Audit | `audit_log` table — immutable, append-only, JSONB detail, no FK constraints |
| Transport | HTTPS enforced in production (K8s ingress TLS termination) |
| Credential leak prevention | `RedactionFilter` strips Authorization/Cookie/X-API-Key from downstream requests |
| LLM prompt injection | Input sanitization before building agent prompts; model outputs validated by `HallucinationDetectorAgent` |
| SpEL sandboxing | `SimpleEvaluationContext` (read-only) — no arbitrary Java execution via policy expressions |
| Open endpoints | Actuator (`/actuator/**`) and Swagger UI bypass authentication; all `/api/**` requires JWT |

---

## Observability

### Implemented Metrics (Micrometer → Prometheus)

```
# Agent subsystem (Phase 10)
aether.agent.executions{agent, decision}   -- counter, incremented per agent execution
aether.agent.latency{agent}                -- timer, per-agent processing latency in ms

# Planned additional meters
aether.proxy.calls.total{tenant, endpoint, outcome}
aether.proxy.latency.ms{tenant, endpoint}  -- histogram
aether.memory.embeddings.total{tenant, type}
aether.memory.similarity.queries.total{tenant}
aether.policy.evaluations.total{tenant, result}
aether.policy.violations.total{tenant, rule}
aether.circuit.breaker.state{tenant, endpoint}
```

### Distributed Tracing (OpenTelemetry → Grafana Tempo)

`aether-api` is wired with `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp`. Every inbound request gets a trace. Planned spans:

- `proxy.filter.tenant-auth`
- `proxy.filter.redaction`
- `proxy.filter.rate-limit`
- `proxy.filter.capture`
- `memory.embed`
- `memory.store`
- `agent.orchestrate`
- `agent.{agent-name}.process`

Trace IDs propagated to downstream APIs via `traceparent` header (W3C Trace Context).

### Infrastructure

- Prometheus scrapes `host.docker.internal:8080/actuator/prometheus` and `:8081/actuator/prometheus`
- Grafana runs at `http://localhost:3000`
- Docker Compose includes `prom/prometheus:v2.55.0` and `grafana/grafana:11.3.0`

---

## EEIK Bootstrap Integration

AetherGrid's development environment is governed by [eeik-bootstrap](https://github.com/suplab/eeik-bootstrap):

- **19 Claude Code agents** in `.claude/agents/` (java-developer, architect, ai-engineer, ai-governance-officer, security-auditor, dba-advisor, kubernetes-engineer, ci-engineer, performance-engineer, technical-writer, etc.)
- **5 slash commands**: `/estimate`, `/review`, `/adr`, `/memory-update`, `/security-scan`
- **Persistent memory**: `.claude/memory/` with 7 files seeded with AetherGrid-specific context
- **Safety hooks**: pre-write guard (blocks dangerous paths), pre-bash guard (blocks destructive git/SQL), post-edit check (warns on `@Autowired`, `javax.*`, `SELECT *`)
- **10 golden rules** enforced at code review: constructor injection, no hardcoded secrets, SLF4J, explicit SQL columns, parameterized queries, `jakarta.*`, Conventional Commits, no TODOs, SOLID, DDD

---

## Build Requirements

| Requirement | Value |
|---|---|
| Java | 21 (enforced by Maven Enforcer) |
| Maven | 3.9+ (enforced by Maven Enforcer) |
| Compiler flags | `--enable-preview`, `-parameters` (required for Spring MVC path variable names) |
| JaCoCo coverage gate | 80% line coverage (all modules) |

---

## CI/CD Pipeline

### GitHub Actions — CI Workflow (`.github/workflows/ci.yml`)

Triggers on every push to every branch. Runs a single `build` job with a 20-minute timeout.

Key steps:
1. Checkout with `actions/checkout` (SHA-pinned)
2. `actions/setup-java` with Temurin 21 distribution and Maven cache
3. Spin up `pgvector/pgvector:pg16` as a service container — exposes PostgreSQL on port 5432 so integration tests connect to a real database without a separate Docker Compose step
4. `mvn --no-transfer-progress verify -pl !aether-infra` — compiles all modules, runs unit and integration tests, enforces the JaCoCo 80% line coverage gate
5. Upload Surefire XML reports and JaCoCo HTML reports as artifacts (7-day retention)

All action references are pinned to full commit SHAs. `permissions: contents: read` is set at the job level (least-privilege).

### GitHub Actions — Quality Gate Workflow (`.github/workflows/quality-gate.yml`)

Triggers on pull requests targeting `main` only. Two parallel jobs:

| Job | Timeout | What it does |
|---|---|---|
| `checkstyle` | 15 min | Runs `mvn checkstyle:check` against `google_checks.xml` via `maven-checkstyle-plugin:3.6.0`; fails the build on any violation |
| `dependency-audit` | 30 min | OWASP Dependency Check with `failBuildOnCVSS=9`; uploads HTML report as artifact (14-day retention); accepted false positives suppressed via `.github/owasp-suppressions.xml` |

The `maven-checkstyle-plugin:3.6.0` is declared in the parent `pom.xml` `pluginManagement` block so all modules inherit the same configuration without repeating it.

### Kubernetes Deployment (`aether-infra/k8s/`)

Apply the full manifests with:

```bash
kubectl apply -f aether-infra/k8s/
```

**Namespace:** `aether-grid` — all resources live in this namespace. Create it first or include `namespace.yaml` at the top of the apply list.

**Security posture (both Deployments):**

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop: [ALL]
```

A `/tmp` `emptyDir` volume is mounted to provide the JVM a writable temp directory while keeping the root filesystem read-only.

**HPA ranges:**

| Service | Min replicas | Max replicas | Scale trigger |
|---|---|---|---|
| `aether-api` | 2 | 8 | CPU utilisation ≥ 70% |
| `aether-proxy` | 2 | 16 | CPU utilisation ≥ 70% |

The data plane (`aether-proxy`) has a higher ceiling because it handles all inbound API traffic. The control plane (`aether-api`) is admin-only and scales more conservatively.

**Liveness and readiness probes** target Spring Boot Actuator endpoints:
- Liveness: `GET /actuator/health/liveness` — restarts the container if the JVM is stuck
- Readiness: `GET /actuator/health/readiness` — gates traffic until the application context is fully started and all downstream dependencies are reachable

### Secret Management

`aether-infra/k8s/secrets-template.yaml` documents every required `Secret` key without embedding any value:

| Key | Consumed by |
|---|---|
| `postgres-url` | Both Deployments |
| `postgres-user` | Both Deployments |
| `postgres-password` | Both Deployments |
| `redis-url` | `aether-proxy` |
| `groq-api-key` | Both Deployments (when `AETHER_LLM_PROVIDER=groq`) |
| `anthropic-api-key` | Both Deployments (when `AETHER_LLM_PROVIDER=anthropic`) |

Operators populate these via their preferred secrets injection mechanism: External Secrets Operator (recommended for production), AWS Secrets Manager, HashiCorp Vault, or `kubectl create secret generic`. The template file is committed; actual values are never committed.

Non-sensitive runtime configuration (Kafka bootstrap servers, JWT issuer, LLM provider selection, rate-limit settings) is in a `ConfigMap` per service and is safe to version-control.

---

## Production Deployment

### Helm Chart Structure (`aether-infra/helm/aether-grid/`)

The Helm chart is the single artefact used for all Kubernetes-based deployments. It supports three deployment flavours selected by values file overlay:

| Flavour | Values file | Ingress type | Registry | Auth |
|---|---|---|---|---|
| Vanilla Kubernetes | `values.yaml` (default) | nginx Ingress Controller | GHCR (`ghcr.io/suplab`) | Helm secret or external |
| AWS EKS | `values-aws.yaml` | AWS ALB (aws-load-balancer-controller) | ECR | IRSA ServiceAccount annotations |
| OpenShift | `values-openshift.yaml` | OpenShift Route (edge TLS) — Ingress disabled | Quay.io | OCP service account |

**Install commands:**

```bash
# Vanilla Kubernetes
helm install aether-grid aether-infra/helm/aether-grid

# AWS EKS
helm install aether-grid aether-infra/helm/aether-grid \
  -f aether-infra/helm/aether-grid/values-aws.yaml

# OpenShift
helm install aether-grid aether-infra/helm/aether-grid \
  -f aether-infra/helm/aether-grid/values-openshift.yaml
```

**Template inventory (20 files):**

- `_helpers.tpl` — shared name, label, and selector helpers
- `NOTES.txt` — post-install operator instructions (service URLs, health check commands)
- `namespace.yaml` — `aether-grid` namespace with standard labels
- `aether-api/` — Deployment, Service, HPA, ConfigMap, ServiceAccount (5 files)
- `aether-proxy/` — Deployment, Service, HPA, ConfigMap, ServiceAccount (5 files)
- `ingress.yaml` — rendered when `openshift.enabled: false`
- `route.yaml` — OpenShift Route with edge TLS; rendered when `openshift.enabled: true`
- `servicemonitor.yaml` — Prometheus Operator ServiceMonitor for both services

**Key Helm design decisions:**

- `securityContext` is OpenShift-aware: `runAsUser` and `fsGroup` are omitted when `openshift.enabled: true` to avoid conflicts with OpenShift SCCs. All other security settings (`allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `capabilities.drop: [ALL]`) apply on all platforms.
- `startupProbe` on each Deployment guards slow application context startup before liveness and readiness probes begin.
- `checksum/config` annotation on each Deployment is computed from the associated ConfigMap content. Helm recalculates this on `helm upgrade`; a changed ConfigMap triggers a rolling pod restart automatically.
- `automountServiceAccountToken: false` on all ServiceAccounts — tokens are only mounted where IRSA or explicit volume mounts require them.

### Dockerfile Multi-Stage Pattern

Both `aether-api/Dockerfile` and `aether-proxy/Dockerfile` follow the same pattern:

```
Stage 1 — build (eclipse-temurin:21-jdk-noble)
  COPY pom.xml files and source
  RUN mvn package -DskipTests --enable-preview

Stage 2 — runtime (eclipse-temurin:21-jre-noble)
  RUN useradd --uid 1000 --gid 1000 aether
  COPY --from=build /app/target/*.jar app.jar
  USER 1000
  HEALTHCHECK --interval=30s CMD curl -f /actuator/health/liveness
  ENTRYPOINT ["java",
    "--enable-preview",
    "-XX:+UseContainerSupport",
    "-XX:MaxRAMPercentage=75.0",
    "-XX:+ExitOnOutOfMemoryError",
    "-jar", "app.jar"]
```

Key choices:
- JRE runtime image reduces attack surface and image size compared to JDK.
- `-XX:+UseContainerSupport` makes the JVM respect cgroup memory limits rather than reading host RAM.
- `-XX:MaxRAMPercentage=75.0` leaves headroom for the OS and sidecar containers.
- `-XX:+ExitOnOutOfMemoryError` causes the JVM to exit (and Kubernetes to restart the pod) on OOM rather than entering an unresponsive degraded state.
- `--enable-preview` is required for Java 21 preview features used in Spring Boot 3.3.x sealed class pattern matching.
- Non-root uid/gid 1000 is created inside the image, not inherited from the base, ensuring consistency across hosts.

### GitHub Actions Container and Helm Pipeline

Two workflows complement the existing CI/CD pipeline introduced in Phase 12:

**`docker-build.yml`** — triggers on push to `main` and `release/**`:

1. OIDC login to GHCR (`docker/login-action`) — no static `DOCKER_PASSWORD` secret stored anywhere
2. Build matrix runs `aether-api` and `aether-proxy` in parallel
3. `docker/build-push-action` builds `linux/amd64` and `linux/arm64` layers via QEMU/Buildx
4. Pushes multi-arch manifest to `ghcr.io/suplab/aether-{api,proxy}:<git-sha>` and `:latest` on main

**`helm-release.yml`** — triggers on push to `release/**`:

1. `helm lint` is run three times: against `values.yaml`, `values-aws.yaml`, and `values-openshift.yaml`
2. `helm template` dry-run validates all three rendered manifests produce parseable YAML
3. `helm push` publishes the chart as an OCI artifact to `ghcr.io/suplab/helm/aether-grid:<version>`

All action references are pinned to full commit SHAs. All jobs use `permissions: contents: read` plus `packages: write` only where the GHCR push step requires it.

---

## Self-Improving Agent Feedback Loop

### Purpose

The feedback loop gives operators a way to tell the system whether an agent decision was correct after the fact. Over time this history drives LLM-powered self-improvement suggestions reviewed on a weekly schedule.

### Domain Model (`aether-domain`)

```
DecisionOutcome (enum)
  CORRECT | INCORRECT | PARTIALLY_CORRECT | UNKNOWN

AgentFeedback (record)
  id UUID
  tenantId TenantId
  agentType String
  decisionId UUID               -- references agent_decisions.id
  originalDecision AgentDecision
  originalConfidence double
  outcome DecisionOutcome
  outcomeDetail String          -- free-text operator note
  recordedAt Instant

AgentFeedbackPort (interface)
  record(AgentFeedback) → void
  findByAgentType(TenantId, String agentType) → List<AgentFeedback>
  getPerformanceStats(TenantId) → Map<String, Object>
```

`AgentFeedbackPort` is defined in `aether-domain` (pure Java, no Spring dependency). `JdbcAgentFeedbackRepository` in `aether-api` is the adapter.

### Database (`V012__agent_feedback.sql`)

The `agent_feedback` table mirrors the record above. Row-level security uses `current_setting('app.tenant_id', true)` — the same policy pattern applied to all other tenant-scoped tables. A composite index on `(tenant_id, agent_type)` makes performance stats queries efficient.

### SelfImprovingAgent (`aether-agents`)

`SelfImprovingAgent` implements `Agent` with capability `SELF_IMPROVEMENT`. When invoked:

1. Reads `AgentFeedback` history for the agent type from `AgentFeedbackPort`
2. Computes outcome statistics (correct/incorrect/partial ratios)
3. Builds a structured LLM prompt with the statistics and a sample of recent decisions
4. Parses the LLM response into improvement suggestions
5. Returns the suggestions as a `SUGGEST` decision with a rationale string

The confidence gate still applies: a `BLOCK` suggestion from this agent auto-enforces only at confidence ≥ 0.95 (the centralized agent-harness gate); anything lower requires human review. `SUGGEST` decisions are informational and never auto-enforced.

### AgentLearningService (`aether-api`)

`AgentLearningService` is annotated `@Scheduled` with a weekly trigger. On each run it:

1. Loads all active tenant IDs
2. For each tenant, constructs an `AgentInput` targeting `SELF_IMPROVEMENT` capability
3. Invokes the `AgentOrchestrator`, which dispatches to `SelfImprovingAgent`
4. Persists the resulting suggestions to `audit_log` via `AuditLogService` for operator review

`LearningConfig` provides the `@Configuration` wiring for this service.

### API Endpoints (`AgentController`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/tenants/{tenantId}/agents/feedback` | Record a `DecisionOutcome` for a past decision. Body: `FeedbackRequest` DTO (agentType, decisionId, originalDecision, originalConfidence, outcome, outcomeDetail). Requires JWT. |
| `GET` | `/api/v1/tenants/{tenantId}/agents/performance` | Returns per-agent-type performance statistics (correct/incorrect/partial counts and ratios) for the tenant. Requires JWT. |

---

## Dashboard and Control Center

### Purpose

The operator dashboard gives visibility into system health, agent activity, memory distribution, and recent decisions without requiring authentication. It is a zero-dependency static HTML file served directly from the `aether-api` process.

### DashboardStatsService (`aether-api`)

`DashboardStatsService` runs direct `NamedParameterJdbcTemplate` queries against the following tables and returns the results as `Map<String, Object>` structures suitable for JSON serialization:

| Query target | What is returned |
|---|---|
| `tenants` | Total active tenant count |
| `memory_embeddings` | Total memory count; count and avg strength grouped by `memory_type` |
| `policies` | Count of ACTIVE policies |
| `agent_decisions` | Total decisions in last 7 days; count grouped by `agent_type` and `decision` |
| `audit_log` | Total audit events in last 7 days |
| `AgentRegistry.registeredTypes()` | Flat list of all registered agent type names |

### DashboardController (`aether-api`)

All endpoints are under `/dashboard/**` and are permitted without authentication (`SecurityConfig` `permitAll()` matcher).

| Method | Path | Description |
|---|---|---|
| `GET` | `/dashboard/stats` | System-wide snapshot: tenant count, total memories, active policies, decisions last 7 days, audit events last 7 days |
| `GET` | `/dashboard/decisions` | Recent agent decisions list. `?limit=20` query param (default 20). Returns agent type, decision, confidence, rationale, decided_at |
| `GET` | `/dashboard/memory-breakdown` | Memory type distribution: type, count, avg strength |
| `GET` | `/dashboard/agent-breakdown` | Per-agent decision counts for the last 7 days |
| `GET` | `/dashboard/agents` | Flat list of registered agent types from `AgentRegistry.registeredTypes()` |
| `GET` | `/dashboard/stream` | Server-Sent Events endpoint. Emits a single stats snapshot on connect. Clients reconnect every 10 seconds for live updates |

### dashboard.html SPA (`aether-api/src/main/resources/static/`)

The dashboard is a self-contained single-page application with no external dependencies (no CDN, no build tool):

- Dark enterprise theme matching the `docs/index.html` color palette
- Stat cards refresh every 10 seconds via `setInterval` + `fetch(/dashboard/stats)`
- Agent registry table populated from `GET /dashboard/agents`
- Memory type breakdown table from `GET /dashboard/memory-breakdown`
- Agent decision breakdown table from `GET /dashboard/agent-breakdown`
- Scrollable recent decisions table from `GET /dashboard/decisions?limit=20`
- SSE live panel consuming `GET /dashboard/stream`; reconnects automatically on disconnect

Served by Spring Boot's default static resource handler at `http://localhost:8081/dashboard.html`. No authentication required — the endpoint is explicitly listed in `SecurityConfig.permitAll()`.

### Security Posture

`SecurityConfig` was updated to add `/dashboard/**` and `/*.html` to the `permitAll()` matcher list. The dashboard exposes only aggregate, non-PII statistics. Individual API call payloads, raw memory content, and tenant API keys are never surfaced.

---

## Aether Core Integration

Aether Grid integrates with [Aether Core](https://github.com/suplab/aether-core) (`suplab/aether-core`) — the personal cognitive engine — via the `PersonalContextPort` interface.

When `aether.core.base-url` is configured, `AetherCoreHttpAdapter` fetches personal user context from Core's `GET /api/v1/personal-context/{tenantId}/{userId}` endpoint before agent decisions. `AetherCoreBridgeAgent` enriches `AgentInput.context` with personal memories, preferences, and emotional state returned by Core.

See [suplab/aether-core](https://github.com/suplab/aether-core) for Core's implementation, schema, and API contract.

---

*See [Roadmap](roadmap.md) · [Progress](progress.md)*
