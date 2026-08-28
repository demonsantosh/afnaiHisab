# Project status — AfnaiHisab

Last updated: 2026-08-28

## Current phase
**Phase 1 — Web MVP, first feature complete in `core`.** Expense/split/balance/settlement (`docs/specs/expense-split-balance.md`, 13 ACs) is fully implemented, tested, independently reviewed, and approved by the user — money-math human-review lane (ADR-0017) satisfied. Not yet wired into `server` (no routes/repositories exist for it) and not yet merged to `main`. Next real step: either wire this feature into `server` + `web`, or continue with more `core` domain logic first — not yet decided.

## Repo / branches
- Remote: https://github.com/demonsantosh/afnaiHisab — `main` and three feature branches pushed, none merged yet.
- `phase-0/kmp-monorepo-scaffold` — Phase 0 scaffolding. PR open, not merged; branch protection on `main` not configured (both manual GitHub steps, still outstanding).
- `phase-1/expense-split-balance-spec` — the spec doc, branched off phase-0.
- `phase-1/expense-split-balance-impl` — current working branch. Contains: TDD red-phase suite, a full best-practice audit's fixes, expert guidelines + project-scoped skills/agents, and now the approved green-phase implementation.

## Reference docs (stable — read once, don't re-derive)
`PLAN.md` (roadmap) · `FEATURES.md` (scope) · `ARCHITECTURE.md` (technical design) · `TOOLING.md` (tool inventory + the corporate-TLS-proxy fix) · `domain-model.md` (entity fields/invariants) · `WORKFLOW.md` (delegation rules + token-optimization policy) · `AGENTS.md` (repo-root cross-tool contract) · `EXPERT_GUIDELINES.md` + `docs/guidelines/{exposed-koin,nextjs-react-typescript}.md` (tool-specific coding standards) · 20 ADRs · `.claude/agents/{afnaihisab-backend,afnaihisab-web}.md` · `.claude/skills/{kotlin,web}-expert-review/`.

## Done
- **Planning** (Phase 0): vision, domain model, architecture, feature tiers, tooling, 20 ADRs — see the reference docs above for what's in each.
- **Phase 0 scaffolding**: `core`/`server`/`web` all build and run; CI workflow written (never actually run yet — no merged PR); GitHub remote connected.
- **Process infrastructure**: `docs/specs/*.md` (EARS spec-before-code), `EXPERT_GUIDELINES.md` + tool-specific guideline docs, `kotlin-expert-review`/`web-expert-review` skills, `afnaihisab-backend`/`afnaihisab-web` project-scoped agents, a documented token-optimization policy (`WORKFLOW.md`).
- **A full best-practice audit** of the actual scaffolded code (not just plans), every real finding fixed: `MinorUnits`/`CurrencyCode` as value classes, `detekt` added, 3 missing FK indexes, Gradle configuration cache enabled, CORS `maxAge`, `renovate.json`, two ADR amendments, a corporate-TLS-proxy JDK issue fixed across every JDK on the machine.
- **Phase 1's first feature — expense/split/balance/settlement — fully implemented in `core`**: `createEqualSplitExpense` (largest-remainder rounding), `calculateBalances` (derived, never stored), `createSettlement`/`recordSettlement` (every settlement reports before/after balance context per the user's explicit clarity requirement). TDD red phase (15 tests) → green phase, independently re-verified from the actual JUnit XML (not a self-report) → user-approved for merge.

## Verified (current)
`./gradlew ktlintCheck detekt --no-daemon` clean · `./gradlew :core:jvmTest` 15/15 **green** · `./gradlew :server:test` 8/8 green · configuration cache stores/reuses cleanly.

## Not started
- `server` routes/repositories for this feature (Exposed table objects don't exist yet — see `docs/guidelines/exposed-koin.md` before writing them).
- Any `web/` UI.
- CI has never run for real (no PR merged, branch protection unconfigured).
- Docker/Postgres still not installed — H2 carries local dev, as planned.
- `Ledger.defaultCurrency`'s real default value — still an open, unresolved question.
- ADR-0007's "simplify debts" algorithm, exact/percentage/weighted/itemized splits — all explicitly Phase 2, not this feature.

## Next concrete step
Not yet decided: wire expense/split/balance/settlement into `server` (repositories per `docs/guidelines/exposed-koin.md`, routes) next, or write another `core` feature first. Separately, not blocking: open/merge the Phase 0 PR and configure branch protection on GitHub — this has been outstanding for a while and should happen soon so CI actually starts running for real.

## Update discipline
Update this file at the end of every work session — phase changes, milestones hit, or scope changes. Prefer rewriting stale sections over appending to them.
