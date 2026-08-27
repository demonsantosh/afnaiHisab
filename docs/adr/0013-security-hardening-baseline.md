# ADR-0013: Security hardening baseline (2FA, encryption at rest, TLS, session timeout, biometric lock)

## Status
Accepted

## Context
ADR-0008 defines the JWT access/refresh token *mechanics*; it doesn't cover the broader security baseline expected of any app handling financial data. Every source consulted treats 2FA, encryption at rest, and TLS as table-stakes for a finance app, not optional hardening — the risk is treating these as "assumed" and never making them explicit engineering tasks.

## Decision
- **2FA**: added in Phase 2 alongside OAuth (ADR-0008's hardening phase) — TOTP-based, not SMS (SMS 2FA has known interception weaknesses, TOTP apps are the current baseline recommendation).
- **Encryption at rest**: Postgres-level encryption (AES-256) for `server`'s database from Phase 2's deploy step onward — explicit deploy-checklist item, not assumed infrastructure.
- **TLS in transit**: enforced from Phase 2's deploy step (no plaintext HTTP once the app leaves localhost) — trivial to state, easy to silently skip if not written down.
- **Session auto-timeout/re-lock**: web (Phase 2) and mobile (Phase 3/4) — idle session re-requires auth after a bounded window.
- **Biometric app-lock** (mobile, Phase 3/4): separate from account login — a device-level re-entry gate (Face ID/fingerprint) distinct from the JWT session itself.

## Consequences
- Phase 2's deploy milestone gets a concrete security checklist (2FA, encryption at rest, TLS) rather than "deploy the app" being ambiguous about what "hardened" means.
- Biometric lock and session timeout are Phase 3/4 mobile-specific work, additive to ADR-0011's `TokenStore` — the biometric gate protects access to the already-secured token store, it doesn't replace it.
- No change to ADR-0002 (sync model) or ADR-0008 (token mechanics) — this ADR is additive security scope layered on top of both.
