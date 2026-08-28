# Project status — AfnaiHisab

Last updated: 2026-08-28

## Current phase
**Phase 1 — Web MVP, TDD red phase.** Phase 0 (scaffolding) is committed, pushed, and its PR is open. Phase 1's first feature (expense/split/balance/settlement) has a written spec, a full red-phase test suite (15 tests, all failing on `NotImplementedError` only — nothing implemented yet), and has already been through one full best-practice audit with real fixes applied. Next real step is the green phase: implementing the logic behind those 15 tests.

## Repo / branches
- Remote: https://github.com/demonsantosh/afnaiHisab — `main` and three feature branches pushed, no branch merged yet.
- `phase-0/kmp-monorepo-scaffold` — Phase 0 scaffolding. PR open: https://github.com/demonsantosh/afnaiHisab/pull/new/phase-0/kmp-monorepo-scaffold (not yet merged; branch protection on `main` not yet configured — both manual GitHub steps).
- `phase-1/expense-split-balance-spec` — the spec doc, branched off phase-0.
- `phase-1/expense-split-balance-impl` — current working branch: TDD red-phase suite, the post-audit fixes (below), and this session's expert-guidelines addition.

## Planning docs (stable, not being actively revised)
`PLAN.md` (roadmap), `FEATURES.md` (scope), `ARCHITECTURE.md` (technical design), `TOOLING.md` (tool inventory + the corporate-TLS-proxy fix), `domain-model.md` (entity fields/invariants), `WORKFLOW.md` (agent delegation rules + token-optimization policy), `AGENTS.md` (repo-root cross-tool contract), `EXPERT_GUIDELINES.md` + `docs/guidelines/*.md` (tool-specific coding standards), 20 ADRs, `.claude/agents/{afnaihisab-backend,afnaihisab-web}.md` (project-scoped delegation agents), `.claude/skills/{kotlin,web}-expert-review/` (project-scoped review gates). Read these once; they don't change per session the way this file does.

## Done this session (2026-08-28)
- **Phase 0 scaffolding reviewed, committed to a feature branch (not `main`), pushed.** GitHub remote created and connected.
- **Phase 1 spec written** (`docs/specs/expense-split-balance.md`, ADR-0016) — 12 ACs, later extended to 13 (AC-13: every settlement reports balance-before/after context, added per explicit user requirement that settlements must never be ambiguous about what was settled).
- **TDD red phase complete**: 15 tests across 5 files (`ExpenseSplittingTest`, `BalanceCalculationTest`, `SettlementValidationTest`, `SettlementRecordTest`, `LedgerMembershipTest`) + deterministic `TestFixtures.kt`, all failing on `NotImplementedError` against fully-KDoc'd `TODO()` stubs (`ExpenseFactory.kt`, `BalanceCalculator.kt`, `SettlementFactory.kt`, `LedgerFactory.kt`). Nothing implemented yet — by design.
- **Full adversarial best-practice audit** (3 parallel research passes against the actual code, not just theory) and every real finding fixed:
  - `MinorUnits`/`CurrencyCode` converted from bare typealiases to `@JvmInline value class`es — compiler now catches money/currency type mix-ups at zero runtime cost.
  - `detekt` added alongside `ktlint`, wired into `check`/CI.
  - 3 missing FK indexes added (`V2__add_missing_fk_indexes.sql` — never edit an applied migration).
  - Gradle configuration cache enabled (caught and fixed two real incompatibilities immediately).
  - CORS `maxAge`, `renovate.json`, two ADR amendments (0005, 0015) recording alternatives considered and rejected, `kotlin.uuid.Uuid`'s experimental-API status documented as a monitored risk.
  - A corporate TLS-intercepting proxy stalled two separate background builds via two *different* unpatched JDKs; fixed by patching every JDK discoverable on this machine, not just the two designated for project use (`TOOLING.md`).
- **`docs/EXPERT_GUIDELINES.md` written** — this project's own distilled expert-level standard (7 sections: Kotlin idioms, domain-layer purity, testing discipline, Ktor conventions, build/tooling hygiene, money-math correctness, git/process), synthesized from the audit above plus standard practice.
- **`kotlin-expert-review` Claude Code skill added** (`.claude/skills/kotlin-expert-review/SKILL.md`) — a project-specific, stricter sibling to generic `/code-review`, enforcing `EXPERT_GUIDELINES.md`. Wired into `AGENTS.md` and `WORKFLOW.md`.
- **Tool-wise expansion, researched fresh (not assumed)**: `docs/guidelines/exposed-koin.md` (Exposed 1.x's completely-changed package structure + current DSL/transaction/Koin-repository conventions — a real gap, since the repository layer isn't written yet) and `docs/guidelines/nextjs-react-typescript.md` (Next.js 16/React 19/TypeScript conventions — a total gap before this, since `web/` had zero guideline coverage). New `web-expert-review` skill for `web/` changes. **ADR-0020** resolved a real architectural fork the research surfaced: `web` stays on client-side `lib/api.ts` for both reads and writes (no Server Actions proxying to Ktor, despite that being 2026's default pattern) — preserves ADR-0003's "web is just another HTTP client" symmetry with future Android/iOS; `useActionState` still adopted for form UX, calling `lib/api.ts` directly rather than via `'use server'`.

## Verified (all green, re-run after every change above)
`./gradlew ktlintCheck detekt --no-daemon` clean · `./gradlew :core:jvmTest` 15/15 red on `NotImplementedError` only · `./gradlew :server:test` 8/8 green · configuration cache stores/reuses cleanly.

## Not started
- Green-phase implementation (the actual logic behind the 15 red tests) — explicitly the next step, not started.
- CI has never run for real (no PR merged yet, branch protection not configured).
- Docker/Postgres still not installed — H2 carries local dev, as planned.
- `Ledger.defaultCurrency`'s real default value — still an open, unresolved question.

## Next concrete step
Implement the green phase against `docs/specs/expense-split-balance.md`'s 13 ACs, using TDD discipline (ADR-0009) and running `/kotlin-expert-review` before considering it done — this is squarely ADR-0017's money-math human-review lane. Separately, and not blocking: open/merge the Phase 0 PR and configure branch protection on GitHub.

## Update discipline
Update this file at the end of every work session — phase changes, milestones hit, or scope changes. Prefer rewriting stale sections over appending to them; this file has already needed one full consolidation (2026-08-28) after accumulating too much session-by-session narrative. This file (not git log or memory) is the source of truth for "where are we."
