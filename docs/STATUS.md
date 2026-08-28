# Project status — AfnaiHisab

Last updated: 2026-08-28

## Current phase
**Phase 1 — Web MVP, first feature complete in `core`, everything on `main`.** Expense/split/balance/settlement (`docs/specs/expense-split-balance.md`, 13 ACs) is fully implemented, tested, independently reviewed, and approved by the user — money-math human-review lane (ADR-0017) satisfied. Not yet wired into `server` (no routes/repositories exist for it). Next real step: either wire this feature into `server` + `web`, or continue with more `core` domain logic first — not yet decided.

## Repo / branches
- Remote: https://github.com/demonsantosh/afnaiHisab — single `main` branch, everything merged. Per ADR-0017's 2026-08-28 amendment, PRs are optional ceremony for solo development (confirmed: no second developer currently) — direct merge after review + a passing CI run is the standing process now. The three earlier feature branches (Phase 0 scaffolding, the spec, the impl) were fast-forward-merged and deleted, both locally and on origin.
- One thing to know: `main` briefly diverged because a `README.md` title was added directly via GitHub's web UI while this session was working on a feature branch — merged cleanly (no conflict, `README.md` was untouched by anything else).

## Reference docs (stable — read once, don't re-derive)
`INDEX.md` (documentation map + change-impact table — read this first if unsure what else to update) · `PLAN.md` (roadmap) · `FEATURES.md` (scope) · `ARCHITECTURE.md` (technical design) · `TOOLING.md` (tool inventory + the corporate-TLS-proxy fix) · `domain-model.md` (entity fields/invariants) · `WORKFLOW.md` (delegation rules + token-optimization policy) · `AGENTS.md` (repo-root cross-tool contract) · `EXPERT_GUIDELINES.md` + `docs/guidelines/{exposed-koin,nextjs-react-typescript}.md` (tool-specific coding standards) · `adr/README.md` (ADR index — don't hardcode a count elsewhere) · `.claude/agents/{afnaihisab-backend,afnaihisab-web}.md` · `.claude/skills/{kotlin,web}-expert-review/`.

## Done
- **Planning** (Phase 0): vision, domain model, architecture, feature tiers, tooling, a full ADR set (`docs/adr/README.md` for the current index) — see the reference docs above for what's in each.
- **Phase 0 scaffolding**: `core`/`server`/`web` all build and run; CI workflow written; GitHub remote connected.
- **Declarative UI made explicit** (ADR-0021): every platform's UI is declarative (React, Compose Multiplatform UI, SwiftUI-or-Compose-Multiplatform-UI) — this was already implicit in ADR-0003/ADR-0010 but never stated as its own principle. Also settles a real gap: Android's choice is explicitly Compose Multiplatform UI, not Android-only Jetpack Compose, since only the multiplatform version keeps sharing UI code with iOS a real option later.
- **Process infrastructure**: `docs/specs/*.md` (EARS spec-before-code), `EXPERT_GUIDELINES.md` + tool-specific guideline docs, `kotlin-expert-review`/`web-expert-review` skills, `afnaihisab-backend`/`afnaihisab-web` project-scoped agents, a documented token-optimization policy (`WORKFLOW.md`).
- **A full best-practice audit** of the actual scaffolded code (not just plans), every real finding fixed: `MinorUnits`/`CurrencyCode` as value classes, `detekt` added, 3 missing FK indexes, Gradle configuration cache enabled, CORS `maxAge`, `renovate.json`, two ADR amendments, a corporate-TLS-proxy JDK issue fixed across every JDK on the machine.
- **Phase 1's first feature — expense/split/balance/settlement — fully implemented in `core`**: `createEqualSplitExpense` (largest-remainder rounding), `calculateBalances` (derived, never stored), `createSettlement`/`recordSettlement` (every settlement reports before/after balance context per the user's explicit clarity requirement). TDD red phase (15 tests) → green phase, independently re-verified from the actual JUnit XML (not a self-report) → user-approved for merge.

## Verified (current)
`./gradlew ktlintCheck detekt --no-daemon` clean · `./gradlew :core:jvmTest` 15/15 **green** · `./gradlew :server:test` 8/8 green · configuration cache stores/reuses cleanly.

## System-design review (2026-08-28, two passes)
**First pass**, four real gaps found and fixed as ADRs: **ADR-0022** (non-functional requirements never stated — now explicit: small-scale, best-effort availability, strong consistency within a ledger), **ADR-0023** (idempotency keys on mutating endpoints — a naive client retry would otherwise duplicate an expense/settlement), **ADR-0024** (ledger-membership authorization as an explicit, human-review-required rule — closes a potential IDOR gap), **ADR-0025** (backup/DR — Neon's free tier is only 6h PITR, never evaluated when chosen for uptime/cost alone).

**Second pass ("re-review, more detailed"), four more real gaps**: **ADR-0026** (operational limits — pagination cap, request body size limit, timeouts, graceful shutdown — none previously specified), **ADR-0027** (all timestamps UTC internally, localization client-only — stated before more date fields accumulate implicit assumptions), **ADR-0028** (API v1 stays additive-only once mobile clients exist — a real requirement given Phase 3/4's app-store-versioning reality, decided now so the convention is already in place before the first mobile client ships), **ADR-0029** (periodic data-integrity reconciliation — split-sums and per-ledger balance-nets-to-zero checks, as defense in depth independent of `core`'s write-path validation, the same pattern real accounting systems use). Also: ADR-0012's audit-log scope widened to include Membership changes, not just Expense/Settlement; ADR-0015 amended with a secrets-rotation ("break glass") requirement.

Both passes validated the same thing, not found it broken: the append-only/derive-on-read balance design is race-condition-safe by construction — confirmed twice now, not just assumed once.

## Not started
- `server` routes/repositories for this feature (Exposed table objects don't exist yet — see `docs/guidelines/exposed-koin.md` before writing them, including the idempotency-key, multi-row-transaction-atomicity, and operational-limits requirements).
- The periodic Postgres backup export (ADR-0025) and data-integrity reconciliation query (ADR-0029) — both process/discipline items, not yet actually set up, low urgency while staging holds only test data.
- The true end-to-end (real-HTTP-API) test now required by Phase 2's "done when" criteria — nothing this integrated exists yet, only per-layer unit/integration tests.
- Any `web/` UI.
- CI has never actually run against `main` yet (first push to trigger it happens whenever the next push lands — nothing blocking this now that everything's on `main` directly).
- Docker/Postgres still not installed — H2 carries local dev, as planned.
- `Ledger.defaultCurrency`'s real default value — still an open, unresolved question.
- ADR-0007's "simplify debts" algorithm, exact/percentage/weighted/itemized splits — all explicitly Phase 2, not this feature.
- Compose Multiplatform UI's actual Gradle setup for Android (ADR-0021) — a Phase 3 concern, not urgent now, but flagged so it isn't discovered mid-implementation.

## Next concrete step
Not yet decided: wire expense/split/balance/settlement into `server` (repositories per `docs/guidelines/exposed-koin.md`, routes) next, or write another `core` feature first.

## Update discipline
Update this file at the end of every work session — phase changes, milestones hit, or scope changes. Prefer rewriting stale sections over appending to them.
