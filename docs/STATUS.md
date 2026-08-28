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

## Cross-document consistency sync + testing-strategy amendment (2026-08-28)
Audited every doc against every other (not assumed in sync) — found and fixed three real drifts: `AGENTS.md`'s "Never do" list was missing 2 of 6 current human-review lanes, `kotlin-expert-review`'s frontmatter description was out of sync with its own body, and `domain-model.md` never documented ADR-0023's idempotency-key table or ADR-0012's Membership-audit widening. Fixed all three, then added the structural fix: `docs/adr/README.md` (ADR index) and `docs/INDEX.md` (documentation map + change-impact table), removing hardcoded ADR counts from three docs' prose so the number can't drift again.

Also amended **ADR-0009**: essential test categories beyond unit tests, made explicit rather than left implicit — integration tests (already the pattern), a required real-HTTP API/contract test before Phase 2, explicit authorization tests (non-member rejected, ADR-0024) and idempotency tests (ADR-0023) per relevant route, and property-based testing specifically for the settle-up algorithm and rounding rule (upgraded from "optional"). Load/mutation/visual-regression testing explicitly deferred per ADR-0022's small-scale NFRs.

## Auth/user-management/i18n gaps closed (2026-08-28)
Prompted by "is localization planned, since it'd be a global app" — audited registration/login/user-management and found real, previously-undecided gaps: **ADR-0030** (Argon2id password hashing — `User.passwordHash` existed since Phase 0 with no algorithm ever chosen; NIST-style length-based strength policy; non-blocking email verification; time-limited single-use password reset, both deferred to Phase 2 since they need real email delivery) and **ADR-0031** (`next-intl` adopted from Phase 1 — every web string wrapped in a translation call now, even though only English exists, since retrofitting hardcoded strings later is the expensive part, not the library setup). Clarified explicitly: "global" means geographic/linguistic diversity, not a user-count change — ADR-0022's small-scale NFRs are unaffected. `domain-model.md` gained `User.emailVerifiedAt` and a documented-now `PasswordResetToken` shape (Phase 2+, same pattern as `AuditLogEntry`/`IdempotencyKey`). Confirmed the `docs/adr/README.md`/`docs/INDEX.md` fix from the previous session already works as designed — `ARCHITECTURE.md`'s header needed no manual update for these two new ADRs.

## iOS UI framework decided early (2026-08-28)
User asked directly whether to decide iOS's UI framework now instead of waiting for Phase 4, as originally planned. Researched current status rather than guess: Compose Multiplatform for iOS reached **Stable in v1.8.0 (May 2025)** — genuinely production-ready (Netflix, Cash App, and an indie 96%-code-sharing example cited), so the maturity-based reason to wait no longer held. **ADR-0032**: iOS uses Compose Multiplatform UI, same as Android (ADR-0021) — decided early, not deferred. Two real, permanent tradeoffs accepted explicitly, not hidden: no automatic iOS-native (HIG) look-and-feel (Skia rendering, not UIKit — the same tradeoff Flutter has), and accessibility (VoiceOver) needs deliberate per-screen semantic mapping, not automatic inheritance. Fixed a stale cross-reference this surfaced: ADR-0010 previously said iOS's framework was "undecided" — now points at ADR-0032. `ARCHITECTURE.md`'s "Platform look-and-feel" section updated from "flagged, not yet decided" to the actual decision.

## Remaining Compose Multiplatform gaps closed (2026-08-28)
User asked directly about Lifecycle/Resources/Multiplatform ViewModel/Navigation — audited and confirmed all four were genuinely undecided (navigation had zero library choice beyond an MVI `Effect` signal; mobile resources/i18n had zero coverage despite ADR-0031 being web-only; ViewModel was cited as an enabling fact for ADR-0010 but never explicitly committed to). Researched and decided: **ADR-0033** (`androidx.navigation`, official/stable since CMP 1.10.0 — not Decompose, whose component philosophy conflicts with ADR-0010's already-rejected framework commitment, and not Voyager, now unnecessary given the official option stabilized), **ADR-0034** (`compose.resources` — the mobile counterpart to ADR-0031's web-only i18n decision), **ADR-0035** (MVI containers extend `androidx.lifecycle.ViewModel`/`viewModelScope`, refining not replacing ADR-0010 — also resolves "Lifecycle" as a side effect, no separate ADR needed). Nice validation surfaced: ADR-0032 (Compose Multiplatform UI on iOS) turns out to also avoid a real SwiftUI rough edge — no Flow-to-Swift bridging layer needed, since both platforms stay in Kotlin. Also fixed two more stale "iOS undecided" references this surfaced, in ADR-0010 and ADR-0021 themselves (not just derived docs) — the drift-hunting discipline `docs/INDEX.md` exists for, applied again.

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
