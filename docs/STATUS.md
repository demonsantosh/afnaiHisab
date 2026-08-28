# Project status — AfnaiHisab

Last updated: 2026-08-28

## Current phase
**Phase 1 — Web MVP, first feature complete in `core`, everything on `main`.** Expense/split/balance/settlement (`docs/specs/expense-split-balance.md`, 13 ACs) is fully implemented, tested, independently reviewed, and approved by the user — money-math human-review lane (ADR-0017) satisfied. Not yet wired into `server` (no routes/repositories exist for it). Next real step: either wire this feature into `server` + `web`, or continue with more `core` domain logic first — not yet decided.

## Repo / branches
- Remote: https://github.com/demonsantosh/afnaiHisab — single `main` branch, everything merged. Per ADR-0017's 2026-08-28 amendment, PRs are optional ceremony for solo development (confirmed: no second developer currently) — direct merge after review + a passing CI run is the standing process now. The three earlier feature branches (Phase 0 scaffolding, the spec, the impl) were fast-forward-merged and deleted, both locally and on origin.
- One thing to know: `main` briefly diverged because a `README.md` title was added directly via GitHub's web UI while this session was working on a feature branch — merged cleanly (no conflict, `README.md` was untouched by anything else).

## Reference docs (stable — read once, don't re-derive)
`PLAN.md` (roadmap) · `FEATURES.md` (scope) · `ARCHITECTURE.md` (technical design) · `TOOLING.md` (tool inventory + the corporate-TLS-proxy fix) · `domain-model.md` (entity fields/invariants) · `WORKFLOW.md` (delegation rules + token-optimization policy) · `AGENTS.md` (repo-root cross-tool contract) · `EXPERT_GUIDELINES.md` + `docs/guidelines/{exposed-koin,nextjs-react-typescript}.md` (tool-specific coding standards) · 21 ADRs · `.claude/agents/{afnaihisab-backend,afnaihisab-web}.md` · `.claude/skills/{kotlin,web}-expert-review/`.

## Done
- **Planning** (Phase 0): vision, domain model, architecture, feature tiers, tooling, 21 ADRs — see the reference docs above for what's in each.
- **Phase 0 scaffolding**: `core`/`server`/`web` all build and run; CI workflow written; GitHub remote connected.
- **Declarative UI made explicit** (ADR-0021): every platform's UI is declarative (React, Compose Multiplatform UI, SwiftUI-or-Compose-Multiplatform-UI) — this was already implicit in ADR-0003/ADR-0010 but never stated as its own principle. Also settles a real gap: Android's choice is explicitly Compose Multiplatform UI, not Android-only Jetpack Compose, since only the multiplatform version keeps sharing UI code with iOS a real option later.
- **Process infrastructure**: `docs/specs/*.md` (EARS spec-before-code), `EXPERT_GUIDELINES.md` + tool-specific guideline docs, `kotlin-expert-review`/`web-expert-review` skills, `afnaihisab-backend`/`afnaihisab-web` project-scoped agents, a documented token-optimization policy (`WORKFLOW.md`).
- **A full best-practice audit** of the actual scaffolded code (not just plans), every real finding fixed: `MinorUnits`/`CurrencyCode` as value classes, `detekt` added, 3 missing FK indexes, Gradle configuration cache enabled, CORS `maxAge`, `renovate.json`, two ADR amendments, a corporate-TLS-proxy JDK issue fixed across every JDK on the machine.
- **Phase 1's first feature — expense/split/balance/settlement — fully implemented in `core`**: `createEqualSplitExpense` (largest-remainder rounding), `calculateBalances` (derived, never stored), `createSettlement`/`recordSettlement` (every settlement reports before/after balance context per the user's explicit clarity requirement). TDD red phase (15 tests) → green phase, independently re-verified from the actual JUnit XML (not a self-report) → user-approved for merge.

## Verified (current)
`./gradlew ktlintCheck detekt --no-daemon` clean · `./gradlew :core:jvmTest` 15/15 **green** · `./gradlew :server:test` 8/8 green · configuration cache stores/reuses cleanly.

## Not started
- `server` routes/repositories for this feature (Exposed table objects don't exist yet — see `docs/guidelines/exposed-koin.md` before writing them).
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
