# ADR-0002: Server-authoritative, online-first sync (offline-first deferred)

## Status
Accepted

## Context
Splitwise-style apps and accounting apps both eventually want offline support. But offline-first requires a sync/conflict-resolution protocol designed into the domain model from the start, and building that alongside a first-time KMP learning curve and an unstable domain model is high risk of stalling Phase 1 entirely.

## Decision
Phase 1–4: server is the single source of truth. Clients (web, then Android, then iOS) call the API directly; no local write queue, no conflict resolution. Phase 5 is reserved specifically to revisit this once the domain model has survived contact with real UI and multiple clients.

## Consequences
- Simpler system design now: standard request/response CRUD against Ktor.
- Mobile apps in Phase 3/4 require network connectivity to add/edit expenses until Phase 5.
- Local caching introduced in Phase 3 (SQLDelight, per ADR-0006) must be read-through only, not a write buffer — this ADR is what constrains that.
- Revisit trigger: start of Phase 5, or earlier if real usage makes offline support a blocker rather than a nice-to-have.
