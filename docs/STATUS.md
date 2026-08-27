# Project status — AfnaiHisab

Last updated: 2026-08-27

## Current phase
**Phase 0 — Foundations.** Planning complete, execution started 2026-08-27: git repo initialized (local identity: `get.santoshbhandari@gmail.com` / `Santosh`, distinct from global config), `docs/domain-model.md` written. Repo scaffolding (Gradle/`core`/`server`/`web`) in progress.

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

## In progress
Nothing — planning phase is complete pending the next step below.

## Not started
- Any actual application code (repo scaffolding in progress, see below)

## Next concrete step
Scaffold the Phase 0 repo structure (`core`, `server`, `web`, Koin wiring per ADR-0005) and write the detailed domain model doc, per `docs/PLAN.md` §6.

## Update discipline
Update this file at the end of every work session — phase changes, milestones hit, or scope changes. This file (not git log or memory) is the source of truth for "where are we."
