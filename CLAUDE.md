# CLAUDE.md — Aether Project Brief

> Read this at the start of every session. It is the single source of truth for what this project is, how it is built, and what rules apply.

---

## What This Project Is

**Aether Grid** (`suplab/aether-grid`) is the distributed enterprise intelligence layer of the Aether ecosystem — a smart proxy and governance platform that sits in front of any API ecosystem, orchestrating a mesh of specialised AI agents that learn, self-improve, and enforce policy.

> **Ecosystem navigation**
> | Layer | Repo | Purpose |
> |---|---|---|
> | Aether Philosophy | — | The vision: cognitive fabric connecting humans, memory, and AI |
> | **Aether Core** | [`suplab/aether-core`](https://github.com/suplab/aether-core) | Personal cognitive engine — individual memory, reasoning, emotional context |
> | **Aether Grid** | `suplab/aether-grid` ← **you are here** | Distributed agent mesh — enterprise API governance platform |

**Sister repository:** [`suplab/aether-core`](https://github.com/suplab/aether-core) — Aether Grid integrates with Core via `PersonalContextPort` to enrich agent decisions with individual user context.

**Current status:** Phases 0–17 complete. Phase 17 (Aether Core sister-repo scaffold) is delivered — `suplab/aether-core` is bootstrapped and has matured into a full platform (Phases 0–6). Grid's own roadmap is fully delivered; new ecosystem capability now lands in the sister platforms (Core, Memory, Vault, Flow).

**Two runnable applications:**
- `aether-proxy` — Data Plane (port 8080): Spring Cloud Gateway intercepting all API traffic
- `aether-api` — Control Plane (port 8081): Admin REST API for governance and configuration

**Five library modules:** `aether-domain`, `aether-memory`, `aether-agents`, `aether-policy`, `aether-infra`

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.x (`jakarta.*` exclusively — never `javax.*`) |
| API Gateway | Spring Cloud Gateway |
| Messaging | Apache Kafka (transactional outbox pattern) |
| Database | PostgreSQL 16 + pgvector extension |
| Cache / Rate Limiting | Redis 7 |
| Vector Store | pgvector (primary), Chroma (adapter) |
| LLM Runtime | Ollama (default, local) · Groq cloud · Anthropic Claude — selected via `AETHER_LLM_PROVIDER` env var |
| Embedding Model | all-MiniLM-L6-v2 via Ollama (384-dim vectors) |
| Resilience | Resilience4j (circuit breaker, retry, bulkhead) |
| Policy Rules | Spring EL (SpEL) evaluated against YAML in PostgreSQL |
| Observability | OpenTelemetry + Micrometer + Prometheus + Grafana |
| DB Migrations | Flyway |
| Build | Maven (multi-module), Java 21 compiler (`--enable-preview`, `-parameters` flags required) |
| Local Dev | Docker Compose (full stack with healthchecks) |
| Production | Kubernetes + Helm |
| CI/CD | GitHub Actions (OIDC, no static secrets) |

---

## Bounded Context

Aether is the root namespace. Aether Grid is the distributed layer within it.

- Package root: `com.suplab.aether`
- Module packages: `com.suplab.aether.core`, `com.suplab.aether.proxy`, `com.suplab.aether.memory`, `com.suplab.aether.agents`, `com.suplab.aether.policy`, `com.suplab.aether.api`

---

## Claude Code Agents

Agents live in `.claude/agents/` and activate automatically based on task context.

Key agents for this project:

| Agent | Use when |
|---|---|
| `java-developer` | Implementing Spring Boot classes, writing tests |
| `architect` | Designing module boundaries, evaluating patterns |
| `ai-engineer` | Working on agents, LLM clients, embedding services |
| `ai-governance-officer` | Policy engine, GDPR redaction, audit log |
| `dba-advisor` | Flyway migrations, PGVector queries, SQL optimization |
| `security-auditor` | Auth, secrets management, PII handling |
| `kubernetes-engineer` | K8s manifests, Helm chart, HPA |
| `ci-engineer` | GitHub Actions pipelines |
| `performance-engineer` | Resilience4j config, Redis rate limiting, latency |
| `technical-writer` | Keeping docs/index.html, README, ADRs in sync |

---

## Pre-Coding Checklist

Before writing any code:
- [ ] Which module does this change belong to? Does it respect bounded context?
- [ ] Is there an existing port interface or utility to reuse?
- [ ] Does this change require a new Flyway migration?
- [ ] Does this change affect the data model or API contract? → update `docs/architecture.md`
- [ ] Does this change affect the roadmap status? → update `docs/progress.md` and `docs/roadmap.md`
- [ ] Will this add PII risk? → wire through `GdprRedactionService`

---

## Ten Golden Rules (Non-Negotiable)

1. **Constructor injection exclusively** — no field-level `@Autowired`, no `@Inject`, fields must be `final`
2. **No hardcoded secrets** — all credentials to environment variables; never committed to source
3. **SLF4J with parameterized messages** — never `System.out.println()` or string concatenation in logs
4. **SOLID design principles** — single responsibility, open/closed, Liskov, interface segregation, dependency inversion
5. **DDD bounded contexts** — cross-module calls go through port interfaces, never reach into another module's internals
6. **Explicit column lists in SQL** — never `SELECT *`; always name every column
7. **Parameterized queries only** — no string concatenation for SQL; use `NamedParameterJdbcTemplate`
8. **Conventional Commits** — `type(scope): description` (feat, fix, docs, chore, build, test, refactor)
9. **No `// TODO` in committed code** — if it's not done, don't commit it
10. **`jakarta.*` exclusively** — Spring Boot 3.x; `javax.*` imports are a build-breaking error

### Aether-Specific Constraints
- Agent confidence < 0.8 → **never auto-block** → human-in-the-loop required
- PII must be redacted by `GdprRedactionService` before any persistence
- All DB queries must include `tenant_id` in `WHERE` clause — no cross-tenant data access
- Ollama must be replaceable: all LLM calls go through `LlmClient` interface
- Memory embeddings are 384-dim (all-MiniLM-L6-v2) — changing this requires a full re-embedding migration

---

## Slash Commands

| Command | Purpose |
|---|---|
| `/estimate` | P50/P80/P90 effort estimate (Human Days = Raw Hours ÷ 6.4) |
| `/review` | Code review against golden rules |
| `/adr` | Create an Architecture Decision Record |
| `/security-scan` | Security review of current changes |
| `/deploy-check` | Pre-deployment readiness checklist |
| `/coverage-report` | JaCoCo coverage analysis |
| `/memory-update` | Update `.claude/memory/` files after major decisions |

---

## Memory Files

| File | Contents |
|---|---|
| `project-context.md` | Service inventory, ports, auth, environments |
| `domain-glossary.md` | Aether-specific terminology |
| `decisions.md` | Architecture decisions log |
| `constraints.md` | Hard constraints + golden rules |
| `patterns.md` | Approved patterns in use |
| `tech-debt.md` | Known technical debt |
| `session-log.md` | Rolling session log (auto-updated) |

---

## Prohibited Patterns

- `javax.*` in any Spring Boot 3.x file
- Field `@Autowired` or `@Inject`
- `SELECT *` in any SQL
- Hardcoded passwords, tokens, or connection strings
- `Thread.sleep()` in tests (use Awaitility or Testcontainers)
- Empty `catch` blocks
- `Optional.get()` without guard
- Direct commits to `main`
- `System.out.println()` in any production code
- Agent blocking decisions when confidence < 0.8

---

## Documentation Sync Rule

Every commit that changes system behavior MUST update:
- `docs/progress.md` — mark completed deliverables
- `README.md` — if architecture or scope changed
- `docs/index.html` — if conceptual overview or tech stack changed
- `docs/roadmap.md` — if milestones shift
- `docs/architecture.md` — if architectural decisions change

The HTML page mirrors the README; they must stay in sync.
