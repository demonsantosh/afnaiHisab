# ADR-0019: Development tooling choices (JDK, lint, DB migrations, web tooling, CI)

## Status
Accepted, amended 2026-08-27 (JDK pick only — see below)

## Context
Several concrete tool choices were implied but never decided across earlier ADRs: which JDK version, how database schema migrations happen (Exposed alone doesn't provide this), Kotlin lint/formatting enforcement, the web package manager and test framework, how the KMP framework gets embedded into the iOS Xcode project, and which CI provider/source host actually runs ADR-0017's enforced test gate. A local environment check (2026-08-27) found JDK 17 (Corretto), Node 20, Android SDK, and Xcode 26.6 already installed — Docker and a git repository are not.

## Decision
- **JDK 17 LTS** — originally Corretto 17.0.9; **amended to Homebrew `openjdk@17` (17.0.15)** after Phase 0 scaffolding hit a stale-`cacerts`/corporate-TLS-proxy issue specific to the manually-installed, never-updated Corretto build (full incident and fix in `docs/TOOLING.md`). Homebrew-managed gets refreshed via `brew upgrade`, which is real protection against this recurring.
- **DB migrations: Flyway.** Plain versioned SQL files, pairs cleanly with Exposed/JDBC, simpler than Liquibase's XML/YAML changesets — matches the project's general preference for plain-SQL-first tooling (same reasoning as ADR-0006's SQLDelight pick).
- **Kotlin lint/format: ktlint.** Enforces the official Kotlin style guide directly — matches `AGENTS.md`'s existing "official Kotlin style guide" line with an actual enforcement mechanism, rather than detekt's heavier, more configurable code-smell/complexity focus (not needed at this project's current scale).
- **Web package manager: npm.** Already installed, zero extra setup. pnpm's speed/disk-efficiency benefits matter more at multi-package monorepo scale than for one Next.js app in Phase 1 — not worth an extra install for a benefit this project doesn't need yet.
- **Web testing: Vitest + React Testing Library** (unit/component) **+ Playwright** (E2E) — Vitest is the current fast/Jest-compatible default for a Next.js app; Playwright is already available as an MCP tool in this Claude Code environment, so manual E2E checks during development reuse the same tool as the automated suite.
- **iOS framework embedding: Swift Package Manager**, not CocoaPods — Xcode-native, avoids adding a Ruby/CocoaPods dependency chain for a project that doesn't otherwise need one.
- **CI: GitHub Actions. Source hosting: GitHub.** Natural fit — free, integrates directly with Koyeb/Vercel/Neon's GitHub-based deploy hooks (ADR-0018), and is what ADR-0017's enforced test gate actually needs to run on. No git repository exists yet in this project — this is a Phase 0 prerequisite, not yet done.
- **Manual API testing during development: IntelliJ's built-in HTTP client** (`.http` files, committed alongside the code they test) — zero extra install, versionable, sufficient for this project's scale over a separate tool like Postman/Bruno.

## Consequences
- No new JDK, package manager, or mobile SDK install required — Phase 0 can start with what's already on the machine except Docker and git.
- Docker is still required for local Postgres per `docs/PLAN.md`'s Phase 0 plan; H2 remains the documented zero-install fallback if Docker isn't installed yet when Phase 0 work starts.
- `git init` + creating a GitHub remote is now an explicit Phase 0 prerequisite, not an implicit assumption — nothing about ADR-0017's commit/branch/CI conventions can happen without it.
- Full current tool inventory, versions, and install-or-not status maintained in `docs/TOOLING.md` — that doc, not this ADR, is what gets updated as versions change.
