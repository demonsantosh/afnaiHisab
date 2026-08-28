# Spec: Registration and login

Per ADR-0016. Implements ADR-0008 (JWT access/refresh mechanics) and ADR-0030 (Argon2id hashing, length-based password strength) as actual, callable server routes — the genuine entry point into the app. Every other endpoint (`docs/specs/expense-split-balance-api.md`) assumes an already-authenticated user; this spec is what makes that assumption real instead of a test-only bypass.

## Summary
`POST /api/v1/auth/register` and `POST /api/v1/auth/login`. Registration auto-issues a token pair on success (register → immediately logged in), matching standard consumer-app UX — no separate login call required right after registering. Email verification and password reset are explicitly Phase 2 (ADR-0030) and out of scope here.

## Acceptance criteria (EARS format)

**Registration**
- AC-R1: WHEN a registration request has a valid email, a password of at least 8 characters (ADR-0030 — length only, no composition rules), and the email is not already registered to a non-ghost user, THE system SHALL create a `User` with an Argon2id-hashed password, issue a new access + refresh token pair (ADR-0008), and return `201` with the tokens.
- AC-R2: WHEN a registration request's email is already registered to a non-ghost user, THE system SHALL reject with `409` and the standard error envelope, creating no record.
- AC-R3: WHEN a registration request's password is under 8 characters, THE system SHALL reject with `400`, `field = "password"`.
- AC-R4: WHEN a registration request's email is not a valid email shape, THE system SHALL reject with `400`, `field = "email"`.
- AC-R5 (ghost-user claim, forward-looking note only): a ghost user (`isGhost = true`) registering with their already-invited email is explicitly **out of scope** for this spec — "claiming" a ghost account is Phase 2 (`docs/FEATURES.md` §b). For now, an email matching an existing ghost user follows the same path as any other new registration would need to (not decided here) — flag this as unresolved rather than guessing at Phase 2 behavior now.

**Login**
- AC-L1: WHEN a login request has the correct email and password for an existing non-ghost user, THE system SHALL issue a new access + refresh token pair and return `200`.
- AC-L2: WHEN a login request has an incorrect password or an email with no matching non-ghost user, THE system SHALL reject with `401`, using an **identical** response for both cases — never reveal which one was wrong (standard practice preventing email enumeration).
- AC-L3: WHEN a login request targets a ghost user (`isGhost = true`, no `passwordHash`), THE system SHALL reject with the same `401` as AC-L2 — ghost users cannot log in (`docs/domain-model.md`).

**Explicitly not idempotency-key endpoints (ADR-0023 doesn't apply here)**
- Registration's natural retry behavior already lands correctly without a key: a retried registration with the same email hits AC-R2's conflict path, not a duplicate account. A retried login just issues a fresh, valid token pair — safe in effect without needing explicit idempotency handling. Rate limiting (ADR-0015, Phase 2) is the actual abuse-prevention mechanism for this endpoint pair, not built now.

## Out of scope
Email verification, password reset (both ADR-0030 — need real email delivery, Phase 2), OAuth (Phase 2), 2FA (Phase 2), rate limiting (Phase 2), ghost-user account claiming (Phase 2, AC-R5 above).

## Test plan
- Registration: happy path (AC-R1) and each rejection (AC-R2, AC-R3, AC-R4).
- Login: happy path (AC-L1) and each rejection (AC-L2, AC-L3), explicitly asserting AC-L2's two failure causes produce byte-identical responses.
- **Explicit security test**: no response body, and no log line anywhere in the request path, ever contains the raw password or the `passwordHash` value — not just "the happy path works," a test that actively searches for leakage.

## Human-review-required?
Yes — auth/token handling is one of ADR-0017's original human-review lanes.
