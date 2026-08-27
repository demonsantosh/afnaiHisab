# ADR-0001: Monorepo with a shared module tree from day 1

## Status
Accepted, amended 2026-08-27 (module naming/granularity only — the core decision is unchanged)

## Context
Phase 1 ships a web-only MVP, but the project's stated goal is to reach Android and iOS via KMP without rewriting business logic. If domain/business logic lives inside `backend/` route handlers during Phase 1, Phase 3 (Android) becomes translation work instead of reuse.

## Decision
Single Gradle multiplatform repo. A shared module exists starting in Phase 0, even though only `server/` consumes it until Phase 3. All split/balance/ledger business rules live in the shared module, never in `server/routes/*`.

## Amendment (2026-08-27)
JetBrains' current (2026) recommended KMP project structure uses one `core` module shared between server and clients, with `server` and per-platform apps (`app/androidApp`, `app/iosApp`) alongside it — replacing the older pattern of splitting shared code into multiple top-level Gradle modules. Adopting this naming/granularity to match current tooling and Gradle-wizard conventions:
- `shared/domain` + `shared/data` → a single `core` module, with domain/data/validation kept as internal package organization inside `core` rather than separate Gradle modules (fewer build-graph edges, same separation of concerns in source layout).
- `backend/` → `server/` to match the same convention.
This is a structural rename, not a reversal — the reasoning in Context/Decision above (business logic lives in the shared module, never in server routes) is unchanged. See docs/ARCHITECTURE.md for the resulting layout.

## Consequences
- Phase 1 has slightly more setup ceremony (a multiplatform module boundary for a single consumer) than a plain Ktor-only backend.
- Phase 3 (Android) and Phase 4 (iOS) consume `core` directly instead of reimplementing balance math against the HTTP API.
- Discipline required: it's easy to "just quickly" put a business rule in a Ktor route for expedience — that's the leak this ADR exists to prevent.
