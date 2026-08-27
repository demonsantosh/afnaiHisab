# Tooling — AfnaiHisab

Living reference — update this file whenever a tool/version is added, changed, or upgraded. Decision rationale for the choices below lives in ADR-0019 (and the ADRs cross-referenced per section); this doc is the current, practical inventory, not the "why."

Last verified: 2026-08-27, on this machine (macOS/Darwin, arm64) — updated after Phase 0 scaffolding.

## Status legend
✅ installed and ready · ⚠️ needs install/setup before the phase that needs it · 🔜 not needed until a later phase

## Core toolchain (all phases)

| Tool | Version | Status | Notes |
|---|---|---|---|
| JDK | 17 LTS — **Homebrew `openjdk@17` 17.0.15** | ✅ installed | `/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home`. This is the project JDK; `export JAVA_HOME` to it before any Gradle command (see the TLS-proxy section below — other JDKs fail dependency resolution on this network). Also present: Corretto 17.0.9 and 18, Oracle 17.0.2, OpenJDK 20 — set IDE/project SDK explicitly, don't rely on whichever is default. |
| Git | 2.50.1 | ✅ installed | Repo initialized (local identity distinct from global config — see `docs/STATUS.md`). |
| Gradle | 9.3.0 (wrapper) | ✅ committed | `./gradlew` — never install Gradle separately; the wrapper is the version of record. |
| GitHub (remote + Actions CI) | — | ⚠️ not set up | `.github/workflows/ci.yml` is written but has never run: no remote exists yet. Needed for ADR-0017's enforced test gate (which also needs branch protection switched on, see action items) and ADR-0018's Koyeb/Vercel deploy hooks. `gh` CLI not installed locally. |
| Docker | — | ⚠️ not installed | Needed for local Postgres. Phase 0 shipped on the documented H2 fallback instead; install before real Phase 1 persistence work. |
| IntelliJ IDEA | — | not checked | **Community edition** (free) is sufficient for `core`/`server` work. Ultimate is paid and adds DB/Ktor-specific tooling — optional, not required, don't install it by default. |

## Backend (`server`)

| Tool | Choice | Status | Notes |
|---|---|---|---|
| Framework | Ktor 3.5.2 (Netty) | ✅ scaffolded | `server/` — `/api/v1/health`, CORS, status-pages error envelope, call logging |
| ORM/SQL | Exposed 1.5.0 | ✅ scaffolded | JVM-only, backend persistence (ARCHITECTURE.md). Bound to a HikariCP 7.1.0 pool. Note Exposed 1.x packages are `org.jetbrains.exposed.v1.*` |
| DI | Koin 4.2.2 (`koin-ktor`) | ✅ scaffolded | ADR-0005 |
| DB (local, now) | H2 2.4.240, `MODE=PostgreSQL` | ✅ in use | File-backed at `.data/afnaihisab.mv.db` (gitignored). Zero-install fallback per below; migrations are written in the Postgres ∩ H2 syntax subset so the switch is a one-line `DATABASE_URL` change |
| DB (target) | Postgres | ⚠️ needs Docker | Still not installed. Local via Docker; Neon in staging (ADR-0018) |
| Migrations | Flyway 13.4.0 | ✅ scaffolded | ADR-0019 — plain versioned SQL. `server/src/main/resources/db/migration/V1__init.sql`. H2 support ships inside `flyway-core`; real Postgres will additionally need `flyway-database-postgresql` |
| Config/secrets | `dotenv-kotlin` 6.5.1 | ✅ scaffolded | Real env vars win over `.env`, so CI and deploys never ship a `.env` (ADR-0015) |
| Manual API testing | IntelliJ `.http` files | ✅ started | `server/api.http`, committed alongside the endpoints it tests — ADR-0019 |

## Web (`web`)

| Tool | Choice | Status | Notes |
|---|---|---|---|
| Runtime | Node.js | ✅ v20.19.2 installed | Node 20 LTS's support window ends ~April 2026 — already past that as of this doc's date; fine for development now, revisit before any production reliance. |
| Package manager | npm | ✅ 10.8.2 installed | ADR-0019 — no pnpm install needed |
| Framework | Next.js 16.3.3 + React 19.2.8 | ✅ scaffolded | ADR-0003. App Router, TypeScript, no Tailwind. **Note:** Next 16 ships its own docs in `web/node_modules/next/dist/docs/` and an auto-generated `web/AGENTS.md` warning that APIs differ from older training data — read those before writing web code |
| Unit/component tests | Vitest + React Testing Library | 🔜 Phase 1 | ADR-0019 |
| E2E tests | Playwright | 🔜 Phase 1+ | Also available as an MCP tool in this Claude Code session for manual checks during development — it was used to verify the Phase 0 health-check round-trip |
| Lint/format | ESLint 9 (flat config) + Prettier 3 | ✅ scaffolded | `eslint-config-prettier` last in the chain so ESLint judges code and Prettier owns layout. `npm run lint`, `npm run format`, `npm run format:check` |

## Mobile (Phase 3 Android / Phase 4 iOS)

| Tool | Status | Notes |
|---|---|---|
| Android SDK | ✅ installed | `~/Library/Android/sdk`, `sdkmanager` present |
| Android Studio | not checked | Needed for Phase 3 UI work/emulator management |
| Xcode | ✅ 26.6 installed | Ready for Phase 4 |
| iOS Simulator | ✅ (bundled with Xcode) | — |
| KMP↔iOS framework embedding | Swift Package Manager | ADR-0019 — not CocoaPods |
| SQLDelight Gradle plugin | 🔜 Phase 3 | ADR-0006 |

## Testing (cross-cutting, per ADR-0009 + ADR-0019)

| Layer | Tool |
|---|---|
| `core` domain logic (`commonTest`) | `kotlin.test` + `kotlinx-coroutines-test` |
| `server` integration tests | `ktor-server-test-host` |
| `core` networking | `MockEngine` |
| Coverage | Kover |
| Kotlin lint/format | ktlint (ADR-0019) |
| Property-based testing (optional) | Kotest — only if specifically needed for ADR-0007's settle-up function |
| Web unit/component | Vitest + React Testing Library |
| Web E2E | Playwright |
| Android UI | Compose UI testing (`androidx.compose.ui.test`) — standard, no separate tool |

## Staging/deploy tools (ADR-0018)

| Tool | Purpose |
|---|---|
| Koyeb | Ktor backend hosting (free tier) |
| Neon | Postgres hosting (free tier) |
| Vercel | Next.js hosting (Hobby/free tier) |
| Cloudflare Tunnel | Exposing local dev server to remote testers before staging exists (not needed once Koyeb staging is live) |

## Known environment issue: corporate TLS proxy breaks JVM tooling (fixed 2026-08-27)

This network sits behind a TLS-intercepting proxy (`GME Internal CA` / `GME Internal Root CA`, self-signed, presented for outbound HTTPS). macOS trusts it (installed via corporate profile in the System keychain), so `curl`, `git`, and `npm` all work fine — but **no JDK's bundled `cacerts` trust store trusts it**, since Java doesn't read the OS keychain. This breaks every Gradle/Kotlin/Maven operation that touches the network (`PKIX path building failed: unable to find valid certification path`) — this is what actually caused the first Phase 0 scaffolding attempt to stall for 10 minutes and get killed, not a slow download.

**Fix applied** (2026-08-27) — imported the corporate CA into the JDKs this project uses:
```
JDK_HOME=<path-to-a-jdk>
keytool -importcert -noprompt -trustcacerts -alias gme-internal-root-ca \
  -file <root-cert.pem> -keystore "$JDK_HOME/lib/security/cacerts" -storepass changeit
keytool -importcert -noprompt -trustcacerts -alias gme-internal-ca \
  -file <intermediate-cert.pem> -keystore "$JDK_HOME/lib/security/cacerts" -storepass changeit
```
Certs extracted via `openssl s_client -connect repo.maven.apache.org:443 -showcerts`. Already patched: Corretto 17.0.9 and Homebrew `openjdk@17` 17.0.15.

**Project JDK going forward: Homebrew `openjdk@17` (17.0.15)**, not Corretto 17.0.9 — superseding ADR-0019's original pick. Reason: Corretto 17.0.9 was installed manually in Nov 2023 and never updated (stale `cacerts`, three years old, is exactly what triggers this class of problem); Homebrew's build gets refreshed via `brew upgrade`, which is a real defense against this recurring. Set explicitly per shell/IDE:
```
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home"
```
**Not hardcoded into `gradle.properties`** — this is a local-machine/corporate-network fix, not a project-portability concern; CI (GitHub Actions, public internet, no corporate proxy) needs none of this and would break if a machine-specific path were committed.

**If this breaks again** (new JDK installed, cacerts reset, different machine on this network): re-run the two `keytool` commands above against the new JDK's `cacerts`, using a fresh `openssl s_client -showcerts` capture if the proxy's CA ever rotates.

## Remaining action items (updated 2026-08-27, after Phase 0 scaffolding)

1. **Install Docker and switch to Postgres** before real Phase 1 work. Scaffolding took the documented H2 fallback, so this is deferred, not skipped — `V1__init.sql` is written to apply unchanged to Postgres, and switching is one `DATABASE_URL` line in `.env` plus adding `flyway-database-postgresql` to the version catalog.
2. **Create the GitHub remote** (manually via github.com, or install `gh` CLI to script it) and then, in Settings → Branches, require the `core-and-server` and `web` checks to pass before merging. `.github/workflows/ci.yml` exists but a workflow file alone cannot block a merge — without branch protection, ADR-0017's "enforced test gate" is only advisory. `git init` is done.
3. Confirm JDK 17 is the active SDK in whatever IDE is used (multiple JDKs are installed; don't assume the default) — and specifically the Homebrew `openjdk@17` below, not Corretto.
