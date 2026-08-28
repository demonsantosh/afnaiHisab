# ADR-0030: Account lifecycle — password hashing, strength policy, verification, reset

## Status
Accepted

## Context
`domain-model.md`'s `User.passwordHash: String?` field has existed since Phase 0 scaffolding with no algorithm ever chosen — a real gap in one of ADR-0017's human-review lanes (auth). Three more core account-lifecycle pieces were never addressed at all: password strength requirements, email verification, and password reset ("forgot password") — the last of which is not a nice-to-have but baseline functionality for any password-auth system.

## Decision
- **Password hashing: Argon2id**, via `argon2-jvm` — current OWASP-recommended algorithm (ranked above bcrypt/scrypt in the Password Storage Cheat Sheet), resists GPU/side-channel attacks better than bcrypt. Parameters: memory 19 MiB / iterations 2 / parallelism 1 (OWASP minimum profile; the heavier 46 MiB/1-iteration profile is an acceptable alternative). `core` never sees a raw password or the hash — hashing/verification happens in `server` only.
- **Password strength: length over complexity**, per current NIST 800-63B guidance — minimum 8 characters, no arbitrary composition rules (forced symbols/numbers/mixed-case). Checking against a known-breached-password list (e.g. via a Have-I-Been-Pwned-style API) is a reasonable future enhancement, not required now.
- **Email verification**: exists as a mechanism from Phase 2 (needs real email delivery — not meaningfully testable on Phase 1's localhost), but is **non-blocking** — a new user can use the app immediately after registering. Verification unlocks account-recovery capability (password reset requires a verified email) rather than gating basic usage, matching this project's "don't over-engineer for a personal app" posture.
- **Password reset**: Phase 2 (same email-delivery dependency). A time-limited (1 hour), single-use token emailed to the user; using it invalidates the token and, per ADR-0008's existing session-family-revocation mechanism, should revoke all existing sessions (a password reset is exactly the scenario that mechanism was built for).

## Consequences
- Phase 1's registration/login already needs Argon2id wired in from the start — this isn't deferred, since it's baked into the schema and every password ever hashed from day one.
- Password reset and email verification are Phase 2 scope, alongside OAuth and 2FA (ADR-0013) — all four share the same "needs a real deployed environment" dependency, not an arbitrary grouping.
- `domain-model.md` gains a documented-now, built-in-Phase-2 shape for email verification and password reset state (see that doc) — same pattern already used for `AuditLogEntry`/`IdempotencyKey`.
- This is a human-review-required lane (ADR-0017, "auth/token handling") — the hashing algorithm and reset-token handling specifically, not just token issuance.
