# ADR-0023: Idempotency keys for mutating financial endpoints

## Status
Accepted

## Context
A system-design review flagged a real, currently-unaddressed gap: nothing in this project handles a client retrying a mutating request. A mobile client on a flaky connection (or any client that times out waiting for a response the server actually already processed) that naively retries `POST /expenses` or `POST /settlements` will create a **duplicate** expense or settlement — silently wrong financial data, exactly the failure mode ADR-0007/ADR-0017 already treat as this app's worst case, just from a different cause (a network retry, not a math bug). This gets more, not less, likely once Phase 5's offline-first sync exists (ADR-0002) — a queued local write that partially succeeds before a connection drop is the textbook idempotency scenario.

## Decision
Every mutating financial endpoint (`create expense`, `create settlement`, and any future equivalent — `add member` is borderline but included for consistency) requires an `Idempotency-Key` header: a client-generated UUID unique per logical action attempt, not per HTTP request. Server behavior: on first sight of a key, process the request normally and store `(idempotency_key, response, created_at)`. On a repeated key, return the stored response directly without reprocessing — a retry is indistinguishable from the original call's result, and never creates a second record.

This requires a small new table (`idempotency_keys` or equivalent) and a shared piece of route-handling logic (not duplicated per route) that wraps the "check key, process-or-return-cached" pattern — this belongs in `server`, follows the same DSL/transaction conventions as `docs/guidelines/exposed-koin.md`.

## Consequences
- Every mutating financial route's implementation includes this check from the start — it is part of what "the route is done" means, not a follow-up hardening pass.
- Web (`lib/api.ts`, ADR-0020) and future mobile clients (Phase 3/4) must generate and send this key — a client-side requirement, not just a server one.
- Idempotency keys need their own retention/cleanup policy eventually (they shouldn't accumulate forever) — not designed now; revisit once real usage volume exists to inform a sensible window (e.g. 24h is a common industry default, per Stripe's well-known pattern, but this project's actual retry window hasn't been measured yet).
- This is squarely a human-review-required lane per ADR-0017 (financial-data correctness) — see ADR-0024's amendment to that lane list.
