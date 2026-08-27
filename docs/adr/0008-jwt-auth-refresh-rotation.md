# ADR-0008: Short-lived JWT access token + rotating refresh token

## Status
Accepted

## Context
Phase 1 needs auth (docs/PLAN.md); Phase 2 explicitly calls for hardening it. Deciding the pattern now avoids a breaking auth migration between phases.

## Decision
Access token (~1h expiry) + refresh token (~24h+ expiry), rotation-on-use: the refresh token is single-use and burned on each refresh, reuse of an already-burned token revokes the entire session family (standard theft-detection pattern). Client-side, use Ktor's built-in Bearer Auth `loadTokens`/`refreshTokens` hooks (auto-attach, auto-refresh on 401, retry original request) with a `Mutex` guarding against duplicate concurrent refreshes.

## Consequences
- No OAuth provider integration in Phase 1 (matches docs/PLAN.md's "email/password + JWT to start"); this ADR defines the token mechanics, not the identity provider — OAuth can be layered on later without touching this pattern.
- Refresh-token rotation requires server-side session/family tracking (a table, not just a stateless JWT) — a small but real piece of Phase 1/2 backend design, not deferrable to "later."
- Orthogonal to ADR-0002 (sync model) — this is transport-layer auth, not data sync.
