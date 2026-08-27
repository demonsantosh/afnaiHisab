# Tooling — AfnaiHisab

Living reference — update this file whenever a tool/version is added, changed, or upgraded. Decision rationale for the choices below lives in ADR-0019 (and the ADRs cross-referenced per section); this doc is the current, practical inventory, not the "why."

Last verified: 2026-08-27, on this machine (macOS/Darwin, arm64).

## Status legend
✅ installed and ready · ⚠️ needs install/setup before the phase that needs it · 🔜 not needed until a later phase

## Core toolchain (all phases)

| Tool | Version | Status | Notes |
|---|---|---|---|
| JDK | 17 LTS (Corretto 17.0.9) | ✅ installed | `~/Library/Java/JavaVirtualMachines/corretto-17.0.9`. Also present: Corretto 18, Oracle 17.0.2, OpenJDK 20 — set IDE/project SDK to 17 explicitly (ADR-0019), don't rely on whichever is default. |
| Git | 2.50.1 | ✅ installed | Repo **not yet initialized** in this project — `git init` is a Phase 0 prerequisite, not done. |
| GitHub (remote + Actions CI) | — | ⚠️ not set up | Needed for ADR-0017's enforced test gate and ADR-0018's Koyeb/Vercel GitHub-based deploy hooks. `gh` CLI not installed locally — create the repo via github.com, or install `gh` if you want it scriptable from here. |
| Docker | — | ⚠️ not installed | Needed for local Postgres (Phase 0). H2 is the documented zero-install fallback if you want to start before installing Docker. |
| IntelliJ IDEA | — | not checked | Recommended for `core`/`server` work — Community edition is sufficient; Ultimate adds DB/Ktor-specific tooling, optional. |

## Backend (`server`)

| Tool | Choice | Status | Notes |
|---|---|---|---|
| Framework | Ktor | 🔜 Phase 0 scaffold | — |
| ORM/SQL | Exposed | 🔜 Phase 0 scaffold | JVM-only, backend persistence (ARCHITECTURE.md) |
| DI | Koin | 🔜 Phase 0 scaffold | ADR-0005 |
| DB | Postgres | ⚠️ needs Docker | Local via Docker; Neon in staging (ADR-0018) |
| Migrations | Flyway | 🔜 Phase 0/1 | ADR-0019 — plain versioned SQL files |
| Manual API testing | IntelliJ `.http` files | 🔜 as needed | Committed alongside the endpoints they test — ADR-0019 |

## Web (`web`)

| Tool | Choice | Status | Notes |
|---|---|---|---|
| Runtime | Node.js | ✅ v20.19.2 installed | Node 20 LTS's support window ends ~April 2026 — already past that as of this doc's date; fine for development now, revisit before any production reliance. |
| Package manager | npm | ✅ 10.8.2 installed | ADR-0019 — no pnpm install needed |
| Framework | Next.js + React | 🔜 Phase 0 scaffold | ADR-0003 |
| Unit/component tests | Vitest + React Testing Library | 🔜 Phase 1 | ADR-0019 |
| E2E tests | Playwright | 🔜 Phase 1+ | Also available as an MCP tool in this Claude Code session for manual checks during development |
| Lint/format | ESLint + Prettier | 🔜 Phase 0 scaffold | Standard Next.js defaults |

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

## Before Phase 0 scaffolding can start — action items

1. Install Docker (or start with H2 and switch to Postgres+Docker before real Phase 1 work).
2. `git init` this repository; create a GitHub remote (manually via github.com, or install `gh` CLI if you want that scriptable).
3. Confirm JDK 17 is the active SDK in whatever IDE is used (multiple JDKs are installed; don't assume the default).

Everything else in this table is either already installed or scaffolded automatically by Gradle/npm during Phase 0.
