# AfnaiHisab — Master Plan

## 1. Vision

**Product**: Start as a Splitwise-style shared-expense app (personal + shared ledgers), evolve into a multipurpose accounting app (double-entry capable) without a rewrite.

**Learning goal**: Learn the KMP ecosystem end-to-end by building this for real — Ktor backend, a shared Kotlin domain/data module, and eventually Compose Multiplatform mobile (Android + iOS).

**Sequencing**: Localhost web app first. Every later platform (deploy, Android, iOS, offline) is added one at a time, on top of a domain model designed up front to not need reshaping.

**Cost: $0 through Phase 2 staging.** Every tool (JDK, Kotlin, Gradle, Ktor, Next.js, H2/Postgres, Android Studio, Xcode, IntelliJ Community, ktlint, Flyway — full list in `docs/TOOLING.md`) and every hosting service used up to and including staging (Koyeb, Neon, Vercel Hobby, GitHub + Actions — ADR-0018) is free, no credit card required anywhere in that chain. The only point a cost can enter is a deliberate decision at Phase 2's production-promotion gate (ADR-0018) — never before, and never by default.

## 2. Locked decisions (see docs/adr/)

| # | Decision |
|---|---|
| ADR-0001 | Monorepo, Gradle multiplatform, single shared `core` module from day 1 — even though only `server` consumes it in Phase 1 (amended 2026-08-27 for module naming, see ADR text) |
| ADR-0002 | Server-authoritative, online-first sync to start. Offline-first is a deliberate later phase, not a day-1 requirement |
| ADR-0003 | Phase 1 web UI is React/Next.js against the Ktor API, not Compose Multiplatform Web — revisit once mobile ships |
| ADR-0004 | Domain model unifies "personal" and "shared" as one `Ledger` with N members (N=1 = personal) so the accounting-app pivot is additive, not a fork |
| ADR-0005 | Koin for dependency injection across `server`/Android/iOS |
| ADR-0006 | SQLDelight for the shared mobile data layer (Phase 3+) |
| ADR-0007 | Greedy heap-based debt simplification algorithm, not min-flow |
| ADR-0008 | JWT access token + rotating refresh token (rotation-on-use, family revocation) |
| ADR-0009 | `kotlin.test` in `commonTest` as the primary test surface for domain logic |
| ADR-0010 | Lightweight, hand-rolled MVI for the shared mobile presentation layer (in `core`), not MVVM |
| ADR-0011 | Secure token storage and push notifications via `expect`/`actual` (Keystore/Keychain, FCM/APNs) |
| ADR-0012 | Visible audit log on shared-ledger mutations, starting Phase 2 (Phase 1 is append-only, nothing to audit yet) |
| ADR-0013 | Security hardening baseline: 2FA, encryption at rest, TLS, session timeout, biometric app-lock |
| ADR-0014 | Anonymize (not hard-delete) on GDPR deletion requests, to keep shared-ledger math correct and not conflict with ADR-0012's audit trail |
| ADR-0015 | API conventions: `/api/v1`, cursor pagination, standard error envelope, explicit CORS allow-list, `.env` secrets locally, rate limiting on auth endpoints from Phase 2 |
| ADR-0016 | Spec-driven development: EARS-format acceptance criteria per feature (`docs/specs/`) before implementation, traceable from commits |
| ADR-0017 | Git/commit hygiene, enforced CI test gate, dependency version pinning, and named human-review-required lanes (auth, money math, deletion, audit log) |
| ADR-0018 | Free staging environment (Koyeb + Neon + Vercel Hobby) for multi-user/mobile testing before production, with an explicit promotion gate |
| ADR-0019 | Tooling: JDK 17, Flyway migrations, ktlint, npm, Vitest+RTL+Playwright, Swift Package Manager, GitHub + GitHub Actions |

Full technical rationale: `docs/ARCHITECTURE.md`. Feature scope: `docs/FEATURES.md`. Repo-root agent instructions: `AGENTS.md`. Tool inventory: `docs/TOOLING.md`.

## 3. Domain model v1

- **User** — an account holder
- **Ledger** — a personal ledger (1 member) or a shared/group ledger (N members). Same entity either way.
- **Membership** — a User's membership in a Ledger (role: owner/member)
- **Expense** — an amount, payer, category, date, belongs to a Ledger
- **Split** — a Member's share of an Expense (equal / exact / percentage / weighted / itemized)
- **Settlement** — a recorded payment between two Members that reduces a balance

Balance-per-member is *derived* from Expenses + Splits + Settlements, never stored directly — this is the invariant that has to hold for the Phase 6 accounting generalization to work later (Account/Transaction/Entry will replace Ledger/Expense/Split under the hood, same derivation principle).

## 4. Repo/module layout

Per ADR-0001's amendment (matches JetBrains' current KMP project convention):

```
afnaihisab/
├── core/                # shared Kotlin module — domain, data, validation as internal packages
│   └── src/commonMain/kotlin/
│       ├── domain/      # entities, use-cases, pure Kotlin, no framework deps
│       ├── data/        # repositories, DTOs, serialization, Ktor client
│       └── validation/  # split/balance business rules
├── server/              # Ktor backend — depends on core
├── web/                 # Next.js — talks to server over HTTP (ADR-0003)
├── app/
│   ├── androidApp/      # Phase 3 — depends on core directly
│   └── iosApp/          # Phase 4 — depends on core via Kotlin/Native framework
└── docs/
    ├── adr/
    ├── PLAN.md
    ├── FEATURES.md
    ├── ARCHITECTURE.md
    ├── WORKFLOW.md
    ├── STATUS.md
    └── domain-model.md
```

## 5. Phased roadmap

### Phase 0 — Foundations (current phase)
- Prerequisites per `docs/TOOLING.md`: `git init` + GitHub remote, install Docker (or start with H2), confirm JDK 17 as active SDK
- Finalize domain model v1 (entities, relationships, invariants) — this doc + `docs/domain-model.md`
- Scaffold Gradle multiplatform repo structure (`core`, `server`, `web`) per §4 and Koin wiring per ADR-0005
- Local dev setup: Postgres via Docker (or H2 to start), Ktor skeleton, Next.js skeleton, `.env`-based secrets, CORS allow-list, `/api/v1` prefix per ADR-0015
- CI: compile `core` + `server` + `web`, **and block merge on failing tests** per ADR-0017 (not advisory-only)
- `AGENTS.md` at repo root (already written); `docs/specs/TEMPLATE.md` ready for Phase 1's first feature spec (ADR-0016)
- **Done when**: `./gradlew build` is green, Ktor dev server and Next.js dev server both run locally, a health-check endpoint round-trips between them, and the CI test gate is wired in (not just "compiles").

### Phase 1 — Web MVP, localhost only ← next
- Write a `docs/specs/<feature>.md` (ADR-0016) for each bullet below before implementing it — starting with expense/split/balance, since that's the money-math lane ADR-0017 flags for human review
- Auth: email/password, JWT access + refresh token per ADR-0008 (no OAuth yet)
- Create Ledger (personal + group), invite members
- Add Expense with equal split
- Balance calculation (who owes whom) — algorithm lives entirely in `core`'s domain layer, tested per ADR-0009, using the rounding-remainder rule from `docs/FEATURES.md` §(a)
- View expense history, record a Settlement
- **Done when**: two local users can create a shared ledger, add expenses, and see correct running balances — all on localhost, nothing deployed.

### Phase 2 — Web hardening + deploy
- Exact/percentage/weighted/itemized splits, expense edit/delete with balance recalculation, expense locking after settlement
- Visible audit log on mutations per ADR-0012, group roles/permissions, group archiving
- Settle-up debt simplification per ADR-0007, partial settlements, cross-group balance dashboard, duplicate-expense detection
- Recurring expenses with explicit edit semantics, recurring-bill detection
- Multi-currency conversion, receipt attachment with itemized extraction, search/filter, category breakdown, notification granularity, push notifications, CSV export, non-app members
- OAuth + 2FA layered onto the ADR-0008 token pattern; encryption at rest, TLS, session auto-timeout per ADR-0013
- **Deploy to free staging** — Koyeb (backend) + Neon (Postgres) + Vercel Hobby (web), per ADR-0018 — this is what makes real multi-user testing (not just solo localhost) possible, at $0
- Real multi-user test on staging: at least two people using a shared ledger concurrently, not just solo testing (ADR-0018 promotion-gate criterion #2)
- Production deploy target: still deliberately TBD — decided later against ADR-0018's promotion gate, not now
- Unit tests on `core`'s domain layer — highest-leverage test surface, protects backend and every future mobile client
- **Done when**: app is reachable on the staging URL (not just localhost), split/balance/settle-up math has test coverage, the Phase 2 security checklist (ADR-0013) is met, and at least one real two-person concurrent test has happened on staging.

### Phase 3 — KMP shared module → Android
- `core`'s data layer becomes real multiplatform (expect/actual, ktor-client networking, MockEngine-tested per ADR-0009)
- `app/androidApp` consumes `core` directly — no HTTP-boundary reimplementation
- Local read-through cache via SQLDelight per ADR-0006, server still authoritative per ADR-0002
- Secure token storage (Keystore) + push notifications (FCM) per ADR-0011; biometric app-lock + session timeout per ADR-0013
- Debug builds point at the Phase 2 staging URL directly (Koyeb, already public per ADR-0018) — no tunnel needed since staging exists by this point in the roadmap. Emulator (`10.0.2.2`) or LAN IP only needed for iterating against a local `server` before pushing to staging.
- **Done when**: Android app hits the staging backend (not just localhost), sharing domain/data code rather than reimplementing it, and has been used by a real second tester alongside web.

### Phase 4 — iOS
- Compile `core` to a Kotlin/Native framework; `app/iosApp` in SwiftUI or Compose Multiplatform UI — **decide explicitly at Phase 4 start** (see ARCHITECTURE.md "Platform look-and-feel"), don't default silently
- Reconcile platform gaps surfaced in Phase 3; debug builds against staging same as Android (ADR-0018)
- **Done when**: iOS functionally matches Android against the same staging backend, and ADR-0018's full promotion gate is re-checked before any production decision is made.

### Phase 5 — Offline-first (revisit ADR-0002)
- Local-first storage on mobile (SQLDelight, same schema as Phase 3's cache) with a sync/conflict-resolution layer
- Optionally retrofit web to tolerate eventual consistency
- **Done when**: adding an expense with no network on mobile succeeds locally and reconciles on reconnect.

### Phase 6 — Accounting-app expansion
- Generalize Ledger/Expense to double-entry (Account, Transaction, Entry) without breaking the Splitwise-shaped UI, inheriting ADR-0012's audit log and applying ADR-0014's anonymize-not-hard-delete policy
- Multi-account tracking, envelope/zero-based budgeting (not just "budgets" — category-to-category fund transfers per `docs/FEATURES.md` §(c)), bank reconciliation, net worth tracking
- Reports: category budget vs. actual, P&L-style summaries; OFX-preferred bank import; tax-category tagging
- **Done when**: a personal ledger can represent a basic multi-account budget with reconciliation, not just "who owes whom."

## 6. Immediate next step

Phase 0: scaffold the Gradle multiplatform repo (`core`, `server`, `web`) per §4, wire Koin (ADR-0005), and write `docs/domain-model.md` with entity fields + invariants before any UI code is written.
