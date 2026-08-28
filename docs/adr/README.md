# Architecture Decision Records — index

The single source of truth for "how many ADRs / what are they" — nothing else in this repo should hardcode a count or an enumerated list; link here instead (`docs/INDEX.md` tracks this rule). Update this file in the same commit as any new or status-amended ADR.

| ADR | Status |
|---|---|
| [0001: Monorepo with a shared module tree from day 1](0001-monorepo-shared-module.md) | Accepted, amended 2026-08-27 (module naming/granularity only) |
| [0002: Server-authoritative, online-first sync](0002-server-authoritative-sync.md) | Accepted |
| [0003: Phase 1 web UI is React/Next.js, not Compose Multiplatform Web](0003-web-ui-nextjs-not-compose-web.md) | Accepted — revisit after Phase 4 |
| [0004: Personal and shared expenses are one Ledger entity](0004-unified-ledger-model.md) | Accepted |
| [0005: Koin for dependency injection](0005-dependency-injection-koin.md) | Accepted, amended 2026-08-28 |
| [0006: SQLDelight for the shared mobile data layer](0006-sqldelight-for-shared-data-layer.md) | Accepted — reconsider if Android ships well before iOS |
| [0007: Greedy heap-based debt simplification](0007-settle-up-algorithm.md) | Accepted |
| [0008: Short-lived JWT access token + rotating refresh token](0008-jwt-auth-refresh-rotation.md) | Accepted |
| [0009: kotlin.test in commonTest as the primary test surface](0009-testing-strategy.md) | Accepted |
| [0010: Lightweight, hand-rolled MVI for the shared presentation layer](0010-mvi-presentation-layer.md) | Accepted |
| [0011: Platform-specific APIs via expect/actual](0011-platform-specific-apis.md) | Accepted |
| [0012: Visible audit log for shared-ledger mutations](0012-audit-log-for-mutations.md) | Accepted, starts Phase 2. Amended 2026-08-28 (scope widened to Membership) |
| [0013: Security hardening baseline](0013-security-hardening-baseline.md) | Accepted |
| [0014: Anonymize, don't hard-delete, for GDPR requests](0014-soft-delete-vs-audit-immutability.md) | Accepted |
| [0015: API versioning, pagination, error format, CORS, secrets, rate limiting](0015-api-and-operational-conventions.md) | Accepted, amended 2026-08-28 (RFC 9457 note, secrets rotation) |
| [0016: Spec-driven development — EARS acceptance criteria](0016-spec-driven-feature-development.md) | Accepted |
| [0017: Git hygiene, CI test gate, dependency pinning, human-review lanes](0017-development-workflow-conventions.md) | Accepted, amended 2026-08-28 (PR ceremony optional for solo dev) |
| [0018: Free staging environment before production](0018-staging-environment.md) | Accepted |
| [0019: Development tooling choices](0019-development-tooling-choices.md) | Accepted, amended 2026-08-27 (JDK pick) |
| [0020: Web stays on client-side lib/api.ts, not Server Actions](0020-web-client-side-fetch-not-server-actions.md) | Accepted |
| [0021: Declarative UI on every platform](0021-declarative-ui-across-platforms.md) | Accepted |
| [0022: Non-functional requirements, stated explicitly](0022-non-functional-requirements.md) | Accepted |
| [0023: Idempotency keys for mutating financial endpoints](0023-idempotency-for-mutating-endpoints.md) | Accepted |
| [0024: Ledger membership authorization as an explicit, enforced rule](0024-ledger-authorization.md) | Accepted |
| [0025: Backup/disaster-recovery policy](0025-backup-and-disaster-recovery.md) | Accepted |
| [0026: Operational limits, timeouts, and graceful shutdown](0026-operational-limits-and-timeouts.md) | Accepted |
| [0027: All timestamps UTC internally](0027-timezone-and-date-handling.md) | Accepted |
| [0028: API backward compatibility — v1 stays additive-only](0028-api-backward-compatibility.md) | Accepted |
| [0029: Periodic data-integrity reconciliation](0029-data-integrity-reconciliation.md) | Accepted |

## Human-review-required lanes (canonical list — ADR-0017, amended by ADR-0023, ADR-0024)

Money math (ADR-0007), auth/token handling (ADR-0008/0011/0013), deletion/anonymization (ADR-0014), the audit log's append-only guarantee (ADR-0012), ledger-membership authorization checks (ADR-0024), idempotency-key handling (ADR-0023). Every other doc that references this list (`AGENTS.md`, `docs/WORKFLOW.md`, `.claude/skills/kotlin-expert-review/`) should point here rather than re-enumerate it — see `docs/INDEX.md`.
