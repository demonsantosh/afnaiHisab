# ADR-0015: API versioning, pagination, error format, CORS, secrets, rate limiting

## Status
Accepted

## Context
A documentation review surfaced several operational concerns with no owner anywhere in the docs: Phase 0's "done when" criteria has web and server as separate local origins (different ports) with no CORS decision; nothing states how JWT signing keys or DB credentials are stored/loaded across local dev vs. deployed environments; there's no API versioning, pagination, or error-response convention despite Phase 2 planning unbounded lists (audit log, expense history); and no rate limiting is planned despite auth endpoints being the obvious abuse target. These are standard engineering defaults, not product/business decisions — deciding them now avoids Phase 1/2 improvising inconsistent one-off answers per endpoint.

## Decision
- **API versioning**: all routes under `/api/v1/...` from Phase 0. Cheap to add now, expensive to retrofit once clients exist.
- **Pagination**: cursor-based (not offset) for any potentially-unbounded list — expense history, audit log (ADR-0012). Offset pagination breaks under concurrent inserts, which a shared ledger will have.
- **Error response format**: a single JSON envelope (`{ "error": { "code": "...", "message": "..." } }`) across all endpoints from Phase 1 — every client (web now, mobile later) parses errors one way.
- **CORS**: explicit allow-list, never a wildcard. Phase 0/1 local dev: `http://localhost:3000` (or whatever Next.js's dev port is) allowed on `server`. Phase 2: the deployed web origin only.
- **Secrets management**: `.env` (gitignored) for local dev — JWT signing key, DB credentials, OAuth client secret. Phase 2 deploy uses whatever secret store the chosen hosting target provides (decided alongside the deploy target itself) — never hardcoded, never committed, in either environment.
- **Rate limiting**: basic per-IP/per-user limiting on auth endpoints (login, refresh) from Phase 2, via Ktor's rate-limiting plugin — the obvious abuse target once the app is reachable outside localhost.

## Consequences
- Phase 0 scaffolding includes a CORS config and `/api/v1` route prefix from the start — small upfront cost, avoids a breaking client-facing change later.
- Phase 1 error handling in `server` routes must return the standard envelope from day one, not retrofitted once web + mobile both depend on the shape.
- Secrets management is a Phase 0 concern for local dev (`.env`), but the *production* half of this decision is blocked on the still-open deploy-target choice — revisit at that point, don't leave secrets improvised in the meantime.
- Rate limiting is explicitly Phase 2 scope (once the app is reachable outside localhost) — not needed for a localhost-only Phase 1.
