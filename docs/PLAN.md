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
| ADR-0020 | Web stays on client-side `lib/api.ts` for reads and writes (no Server Actions calling Ktor) — `useActionState` for form UX only |
| ADR-0021 | Every platform's UI is declarative (React, Compose Multiplatform UI, SwiftUI or Compose Multiplatform UI) — Android's choice is explicitly Compose Multiplatform UI, not Android-only Jetpack Compose |
| ADR-0022 | Non-functional requirements stated explicitly: small-scale, best-effort availability, strong consistency within a ledger, interactive-latency only |
| ADR-0023 | Idempotency keys required on mutating financial endpoints — a retried request never creates a duplicate expense/settlement |
| ADR-0024 | Ledger-membership authorization is an explicit, enforced, human-review-required rule on every ledger-scoped route |
| ADR-0025 | Backup/DR: don't rely solely on the hosting provider's default retention (Neon's free tier is only 6h PITR) — periodic independent export required |
| ADR-0026 | Operational limits: pagination page-size cap, request body size limit, timeouts, graceful shutdown on SIGTERM |
| ADR-0027 | All timestamps UTC internally (`Instant`); localization is a client-only display concern, never server-side |
| ADR-0028 | API v1 stays additive-only, forever, once mobile clients exist — a breaking change requires v2, not a v1 mutation |
| ADR-0029 | Periodic data-integrity reconciliation (split-sums, per-ledger balance-nets-to-zero) — defense in depth independent of `core`'s write-path validation |
| ADR-0030 | Account lifecycle: Argon2id password hashing, length-based strength policy, non-blocking email verification, time-limited single-use password reset |
| ADR-0031 | i18n architecture decided now (`next-intl`, every string wrapped from Phase 1) — no translations committed yet, just kept cheap to add later |
| ADR-0032 | iOS uses Compose Multiplatform UI (decided early, not deferred to Phase 4) — same framework as Android; HIG-fidelity and accessibility are explicit, owned design work, not automatic |

System-design review (2026-08-28, two passes): first pass found non-functional requirements, idempotency, authorization, and backup/DR. A deeper second pass found operational limits/timeouts, timezone handling, multi-client API compatibility, and data-integrity reconciliation as defense-in-depth. The append-only/derive-on-read balance design (`docs/PLAN.md` §3) was validated as race-condition-safe by construction, not by locking, in both passes.

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
- Auth: email/password (Argon2id hashing, ADR-0030), JWT access + refresh token per ADR-0008 (no OAuth, email verification, or password reset yet)
- Web strings wrapped via `next-intl` from the first component (ADR-0031) — English-only content, architecture only
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
- Email verification + password reset (ADR-0030) — both need real email delivery, which is why they wait for this phase; profile management (change name/email/password)
- **Deploy to free staging** — Koyeb (backend) + Neon (Postgres) + Vercel Hobby (web), per ADR-0018 — this is what makes real multi-user testing (not just solo localhost) possible, at $0
- Real multi-user test on staging: at least two people using a shared ledger concurrently, not just solo testing (ADR-0018 promotion-gate criterion #2)
- Production deploy target: still deliberately TBD — decided later against ADR-0018's promotion gate, not now
- Unit tests on `core`'s domain layer — highest-leverage test surface, protects backend and every future mobile client
- Operational limits wired per ADR-0026 (pagination cap, request body limit, timeouts, graceful shutdown); idempotency keys (ADR-0023) and ledger-authorization checks (ADR-0024) on every route
- At least one true end-to-end test: create an expense via the real HTTP API, then query balances via the real HTTP API, asserting the correct result — not just per-layer unit tests. Nothing this integrated exists yet; this is a real, currently-open gap, not a formality.
- **Done when**: app is reachable on the staging URL (not just localhost), split/balance/settle-up math has test coverage (including the end-to-end test above), the Phase 2 security checklist (ADR-0013) is met, and at least one real two-person concurrent test has happened on staging.

### Phase 3 — KMP shared module → Android
- `core`'s data layer becomes real multiplatform (expect/actual, ktor-client networking, MockEngine-tested per ADR-0009)
- `app/androidApp` consumes `core` directly — no HTTP-boundary reimplementation
- UI is Compose Multiplatform UI (ADR-0021), not plain Android-only Jetpack Compose — get the Gradle setup right at this phase's start, not mid-implementation
- Local read-through cache via SQLDelight per ADR-0006, server still authoritative per ADR-0002
- Secure token storage (Keystore) + push notifications (FCM) per ADR-0011; biometric app-lock + session timeout per ADR-0013
- Debug builds point at the Phase 2 staging URL directly (Koyeb, already public per ADR-0018) — no tunnel needed since staging exists by this point in the roadmap. Emulator (`10.0.2.2`) or LAN IP only needed for iterating against a local `server` before pushing to staging.
- **Done when**: Android app hits the staging backend (not just localhost), sharing domain/data code rather than reimplementing it, and has been used by a real second tester alongside web.

### Phase 4 — iOS
- Compile `core` to a Kotlin/Native framework; `app/iosApp` in **Compose Multiplatform UI** (ADR-0032, decided early — same framework as Android, ADR-0021) — budget explicit design time for HIG-fidelity theming and per-screen accessibility semantics (ARCHITECTURE.md "Platform look-and-feel"), since neither is automatic the way SwiftUI would have been
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
