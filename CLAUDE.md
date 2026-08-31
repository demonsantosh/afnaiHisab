# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## AfnaiHisab repo conventions

Repo-level facts a session needs *before* touching code, so nothing here has to be re-derived by
reading the tree (`docs/WORKFLOW.md` — "Project-root CLAUDE.md").

- **What/why/when**: `AGENTS.md` (tool-agnostic contract), `docs/STATUS.md` (where the project
  actually stands — read this first, it's updated every session), `docs/PLAN.md` (phases),
  `docs/INDEX.md` (documentation map + change-impact table — check this if unsure what else needs
  updating alongside a change), `docs/adr/` (decisions + rationale, one file per ADR,
  `docs/adr/README.md` is the index), `docs/specs/<feature>.md` (EARS acceptance criteria per
  feature, ADR-0016 — write one before implementing a feature).
- **How this repo is laid out and run**: this file.

Current phase: **Phase 1, in progress.** Registration/login (JWT access+refresh) and a 6-endpoint
ledger/expense/balance/settlement API are implemented in `server` and merged to `main` — see
"API surface" below. Nothing in `web/` calls it yet; the only thing left for Phase 1's "done when"
criterion is a minimal web UI (local auth, create a shared ledger, add an expense, view balances).
Don't assume `web/` has any real pages/components beyond Next.js scaffolding — check before
referencing one.

## Modules and what may depend on what (ADR-0001 — do not violate)

```
core/     Kotlin Multiplatform, commonMain only today (jvm() target).
          domain / data / validation / presentation — pure Kotlin, no framework.
server/   Ktor + Exposed, JVM. Depends on :core. Nothing depends on server.
web/      Next.js + TypeScript. Talks to server over HTTP only (ADR-0003).
          Outside the Gradle build — `settings.gradle.kts` does not include it.
app/      Phase 3 (androidApp) / Phase 4 (iosApp). Not scaffolded yet.
```

The load-bearing rule: **business rules live in `core`, never in a Ktor route handler and never in
a React component.** A route is transport (parse, call a service, respond); a component is
presentation. When a rule appears to need writing twice, that is the signal it belongs in `core`.

`core` is the reason Android/iOS are additive later instead of a rewrite — every shortcut taken in
`server` is a Phase 3 reimplementation.

## API surface (`server/src/main/kotlin/com/afnaihisab/server/routes/`)

All under `/api/v1`, one route file per resource (`AuthRoutes`, `LedgerRoutes`, `ExpenseRoutes`,
`BalanceRoutes`, `SettlementRoutes`, `HealthRoutes`):

```
GET  /health
POST /auth/register
POST /auth/login
POST /ledgers
POST /ledgers/{ledgerId}/members
POST /ledgers/{ledgerId}/expenses
GET  /ledgers/{ledgerId}/expenses
GET  /ledgers/{ledgerId}/balances
POST /ledgers/{ledgerId}/settlements
```

Every mutating route requires a bearer JWT (`type=ACCESS`, not a refresh token — checked
explicitly), enforces ledger-membership authorization (ADR-0024), and accepts an idempotency key
scoped by `(userId, idempotencyKey)` (ADR-0023) — never by the key alone, that was a real
cross-tenant leak found and fixed during review. There is no `POST /auth/refresh` route yet even
though `RefreshSessionRepository`'s rotation/reuse-detection logic exists and is tested.

## Package/naming conventions

- Kotlin packages: `com.afnaihisab.core.*` and `com.afnaihisab.server.*`, mirroring the directory.
  Official Kotlin style guide, enforced by ktlint (ADR-0019) — run `./gradlew ktlintFormat` rather
  than hand-fixing.
- Ids are `kotlin.uuid.Uuid` (UUIDv7 in practice — time-sortable, so an id doubles as a pagination
  cursor). Both modules opt in to `kotlin.uuid.ExperimentalUuidApi` in their build script.
- **Money is `MinorUnits` (a `@JvmInline value class` over `Long`), always** — and a currency is
  `CurrencyCode` (over `String`), never a bare `String`. Never `Double`, `Float`, or `BigDecimal`
  on a money field. Both compile away to the underlying primitive (zero runtime cost) while the
  compiler rejects a stray `Long`/`String` at a money/currency parameter. This is the single most
  important type rule in the repo (`docs/domain-model.md`).
- SQL: lowercase snake_case, plural tables (`expenses`), `ix_`/`ux_`/`ck_` prefixes for
  index/unique/check constraints.
- TypeScript: `web/lib/api.ts` is the only place that knows the backend's URL and error shape;
  components consume it, they do not call `fetch` directly.

## Running the dev stack locally

**Every Gradle command on this machine needs JDK 17 exported first** — a corporate TLS proxy makes
other JDKs fail dependency resolution with `PKIX path building failed`
(`docs/TOOLING.md` — "Known environment issue"):

```bash
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home"
```

First run only:

```bash
cp .env.example .env          # repo root — server config/secrets (ADR-0015), gitignored
cd web && npm ci && cd ..     # exact versions from package-lock.json (ADR-0017)
```

Then two terminals:

```bash
# terminal 1 — Ktor on http://localhost:8080, migrating + serving /api/v1
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home"
./gradlew :server:run

# terminal 2 — Next.js on http://localhost:3000
cd web && npm run dev
```

Open <http://localhost:3000>; the page calls `GET /api/v1/health` **from the browser**, which is
what exercises the CORS allow-list. Or hit it directly:

```bash
curl -s http://localhost:8080/api/v1/health
```

Notes:
- The database is file-backed H2 in PostgreSQL compatibility mode at `.data/afnaihisab.mv.db`
  (gitignored) — no Docker needed. Delete that file to start from an empty schema; Flyway will
  re-run `V1__init.sql`. Switching to real Postgres is one `DATABASE_URL` line in `.env`.
- `:server:run` deliberately runs with the **repo root** as its working directory, because that is
  where `.env` and `.data/` live.
- `server/api.http` holds ready-made requests for IntelliJ's HTTP client (ADR-0019).

## Build, test, lint

```bash
./gradlew build             # compile + ktlintCheck + detekt + all JVM tests (core + server)
./gradlew test              # tests only
./gradlew ktlintFormat      # autofix Kotlin style
./gradlew ktlintCheck detekt  # lint only, no build/tests
cd web && npm run lint && npm run format:check && npm run build   # build type-checks the app
```

Single test: `./gradlew :core:jvmTest --tests "com.afnaihisab.core.domain.BalanceCalculationTest"`
or `:server:test --tests "com.afnaihisab.server.routes.ExpenseRoutesTest"`. Detekt's ruleset lives
at `config/detekt/detekt.yml`, wired into `check` (and thus CI) for both `core` and `server` — a
violation fails the build exactly like a failing test.

`.github/workflows/ci.yml` runs exactly these. Per ADR-0017 the CI result must *block* merges —
that requires branch protection to be switched on in GitHub's settings; the workflow file alone
cannot do it (see the comment at the top of the file).

## Where tests live (ADR-0009)

| What | Where | Tool |
|---|---|---|
| Domain logic — split math, balance calc, settle-up | `core/src/commonTest/kotlin/` | `kotlin.test` + `kotlinx-coroutines-test` |
| Server routes, wiring, migrations | `server/src/test/kotlin/` | `ktor-server-test-host` |
| `core` networking (Phase 3) | `core/src/commonTest/` | Ktor `MockEngine` |
| Web unit/component (Phase 1) | `web/` | Vitest + React Testing Library |
| Web E2E (Phase 1+) | `web/` | Playwright |

Server tests boot the **real** `Application.module(...)` against a fresh in-memory H2, so they
exercise the actual plugin/DI/routing graph and the real Flyway migration — not a hand-assembled
subset. Reuse `testAppConfig()` in `server/src/test/kotlin/com/afnaihisab/server/TestConfig.kt`.

Domain logic gets tests **before** the code that consumes it — it is the highest-leverage surface
in the repo, shared by server + both future mobile clients.

## Things that will bite you

- Adding a schema change means a **new** `server/src/main/resources/db/migration/V<n>__*.sql`.
  Never edit an applied migration — Flyway validates checksums and will refuse to start.
- Keep `V*.sql` inside the PostgreSQL ∩ H2 syntax subset (no partial indexes, no native enum
  types) for as long as H2 is the local database.
- `MigrationTest` asserts the exact column list per table; a migration that renames a column must
  update it.
- CORS is an explicit allow-list, never a wildcard (ADR-0015). Changing it is a `.env`
  (`CORS_ALLOWED_ORIGINS`) change, not a code change.
- Every error response must go through `ApplicationCall.respondError(...)` so it stays in the one
  envelope shape every client parses.
- Human-review-required lanes (ADR-0017) — auth/tokens, balance & settle-up money math,
  deletion/anonymization, the audit log's append-only guarantee, ledger-membership authorization
  (ADR-0024), and idempotency-key handling (ADR-0023). Flag a diff touching these explicitly; they
  are never auto-approved regardless of how the change was generated. This list can drift — check
  ADR-0017 and its amendments if unsure, per `docs/INDEX.md`.
