# Project status — AfnaiHisab

Last updated: 2026-08-27

## Current phase
**Phase 0 — Foundations. Scaffolding complete 2026-08-27, uncommitted and pending review.** Git repo initialized (local identity: `get.santoshbhandari@gmail.com` / `Santosh`, distinct from global config). Every `docs/PLAN.md` §5 Phase 0 "done when" criterion is met locally: `./gradlew build` is green, both dev servers run, and the health check round-trips from the browser through the CORS allow-list to H2 and back. Two items cannot be finished from this machine and remain open: no GitHub remote exists, so ADR-0017's merge-blocking branch protection is unconfigured; and Docker/Postgres is still not installed (H2 is carrying local dev, as `docs/TOOLING.md` allows).

## Done
- Vision, locked architectural decisions, domain model v1, repo layout, and full phased roadmap written (`docs/PLAN.md`)
- Feature requirements researched and tiered (`docs/FEATURES.md`) — Splitwise/competitor feature research + accounting-app standard features
- Technical architecture researched and consolidated (`docs/ARCHITECTURE.md`) — module structure, DI, data layer, settle-up algorithm, auth, testing strategy
- ADR-0001 through ADR-0014 recorded (`docs/adr/`), including ADR-0001's 2026-08-27 module-naming amendment (`shared/` → `core`, `backend/` → `server`), ADR-0010 (MVI presentation layer), ADR-0011 (secure storage + push notifications via expect/actual), ADR-0012 (audit log), ADR-0013 (security hardening baseline), ADR-0014 (anonymize vs hard-delete for GDPR)
- Plan cross-checked against the official Kotlin Multiplatform cross-platform-mobile-development doc's full 10-point benefit list — 8/10 already covered, 2 gaps closed (native-API expect/actual boundary, platform look-and-feel flagged for explicit Phase 4 decision)
- `docs/FEATURES.md` deepened via two further research passes: (1) advanced/edge-case splitting mechanics + 2025-2026 AI-era fintech UX, (2) accounting depth (envelope budgeting, reconciliation, net worth, OFX) + finance-app security/compliance baseline. Phase 1 gained one explicit correctness rule (rounding-remainder allocation); Phase 2 absorbed most fold-ins (audit log, roles, partial settlements, 2FA, encryption at rest, etc.) since Phase 1 is append-only and has nothing yet to audit or harden beyond auth itself.
- Working process / delegation rules defined (`docs/WORKFLOW.md`)
- Full doc review completed 2026-08-27: fixed stale `shared/`/`backend/` references left over from ADR-0001's rename (in ADR-0002/0003/0004, ARCHITECTURE.md, WORKFLOW.md), reconciled split-type and role terminology drift between `PLAN.md`/`FEATURES.md`/`ARCHITECTURE.md`, and closed an operational-conventions gap (API versioning, pagination, error format, CORS, secrets, rate limiting) via new ADR-0015
- Researched how disciplined AI-assisted ("vibe coding") development stays maintainable/handoff-ready: adopted `AGENTS.md` (cross-tool root-level standard, distinct from the Claude-specific `WORKFLOW.md`), spec-driven development for individual features (ADR-0016, `docs/specs/TEMPLATE.md`, EARS-format acceptance criteria before implementation), and development workflow conventions — enforced CI test gate, git/commit hygiene, dependency pinning, named human-review-required lanes (ADR-0017)
- Researched and locked a free staging environment for multi-user/mobile testing before production: Koyeb (backend) + Neon (Postgres) + Vercel Hobby (web), $0 cost, with an explicit promotion-to-production gate (ADR-0018). Phase 2/3/4 in `docs/PLAN.md` updated so mobile debug builds test against staging directly once it exists.
- Full development/testing tooling inventoried and decided (ADR-0019, `docs/TOOLING.md`): JDK 17, Flyway, ktlint, npm, Vitest+RTL+Playwright, Swift Package Manager, GitHub+Actions. Local machine checked 2026-08-27 — JDK 17/Node 20/Android SDK/Xcode 26.6/git already installed; **Docker and a git repository are not** — both are now explicit Phase 0 prerequisites.
- **Corporate TLS proxy diagnosed and fixed** — the first scaffolding attempt stalled because no JDK's `cacerts` trusted the network's intercepting CA, breaking every Gradle dependency resolution with `PKIX path building failed`. Root cause, the `keytool` fix, and the resulting switch to Homebrew `openjdk@17` are written up in `docs/TOOLING.md` and ADR-0019's amendment.
- **Phase 0 scaffolding built (2026-08-27, uncommitted):**
  - Root Gradle build, `gradle/libs.versions.toml` (all versions pinned, ADR-0017), Gradle 9.3 wrapper.
  - `core` — every `docs/domain-model.md` entity as a Kotlin type (money as `MinorUnits`/`Long`), plus `presentation/Mvi.kt` (ADR-0010), `validation/ValidationResult.kt`, `data/api/{CursorPage,ApiError}.kt` (ADR-0015).
  - `server` — Ktor + Netty, `/api/v1/health` reporting service/version/database, Koin DI (ADR-0005), Exposed over a Hikari pool, Flyway `V1__init.sql` covering users/ledgers/memberships/expenses/splits/settlements (no audit log — Phase 2, ADR-0012), H2 in PostgreSQL compatibility mode, CORS allow-list, ADR-0015's error envelope via status-pages, `.env` loading, `server/api.http` for IntelliJ (ADR-0019). 7 integration tests via `ktor-server-test-host`.
  - `web` — Next.js 16 + React 19 + TypeScript, one page whose **client-side** fetch of `/api/v1/health` is what actually exercises CORS, ESLint + Prettier, exact-pinned dependencies with `package-lock.json` committed.
  - `.github/workflows/ci.yml` — `./gradlew build` (compile + ktlint + tests) and web lint/format/build, both failing the run on error (ADR-0017).
  - Root `CLAUDE.md` (module boundaries, conventions, dev-stack commands, where tests live) and `.gitignore`.
  - ktlint wired into `check` for both `core` and `server`, whole tree clean.

## In progress
GitHub remote is live: https://github.com/demonsantosh/afnaiHisab. Both `main` and `phase-0/kmp-monorepo-scaffold` pushed. PR not opened yet, branch protection not configured — both are manual/web-UI steps, not something done from here.

## Not started
- Phase 1 application code — auth, ledgers, expenses, splits, settlements, balances (`docs/PLAN.md` §5)
- `core/src/commonTest` — no domain tests yet, because Phase 0 added types only, no logic

## Next concrete step
Open the PR (https://github.com/demonsantosh/afnaiHisab/pull/new/phase-0/kmp-monorepo-scaffold), let CI actually run for the first time, then in GitHub Settings → Branches add a protection rule on `main` requiring the CI jobs to pass before merge — this is what turns ADR-0017's test gate from a workflow file into an enforced one. After that, Phase 1 begins with a `docs/specs/<feature>.md` for expense/split/balance (ADR-0016) — the money-math lane ADR-0017 flags for human review — before any implementation.

## Update discipline
Update this file at the end of every work session — phase changes, milestones hit, or scope changes. This file (not git log or memory) is the source of truth for "where are we."
