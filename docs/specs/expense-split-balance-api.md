# Spec: Expense/split/balance/settlement — server API layer

Per ADR-0016. Extends `docs/specs/expense-split-balance.md` (13 core-domain ACs, already implemented and approved) to the HTTP layer — routes, repositories, authorization, idempotency, pagination. This is a human-review lane on three counts simultaneously (ADR-0017 money math, ADR-0024 authorization, ADR-0023 idempotency), not just one.

## Summary
Exposes `core`'s already-implemented expense/split/balance/settlement logic via `server` routes. No new business logic — `core`'s functions are the single source of truth for validation and computation; this spec is entirely about the HTTP-layer concerns wrapped around them.

## Endpoints in scope
- `POST /api/v1/ledgers` — create ledger (core AC-11)
- `POST /api/v1/ledgers/{ledgerId}/members` — add member (core AC-12)
- `POST /api/v1/ledgers/{ledgerId}/expenses` — create expense, equal split (core AC-1..AC-5)
- `GET /api/v1/ledgers/{ledgerId}/expenses` — expense history, cursor-paginated
- `GET /api/v1/ledgers/{ledgerId}/balances` — current balances (core AC-6, AC-7)
- `POST /api/v1/ledgers/{ledgerId}/settlements` — record settlement (core AC-8..AC-10, AC-13)

## Acceptance criteria (EARS format)

**Authorization (ADR-0024) — every ledger-scoped endpoint above except ledger creation itself**
- AC-S1: WHEN a request targets a `ledgerId` the authenticated user is not a member of, THE system SHALL reject it with `403` and the standard error envelope, without performing the requested operation or touching any repository.

**Idempotency (ADR-0023) — every `POST` above**
- AC-S2: WHEN a `POST` request includes an `Idempotency-Key` header matching a key already processed for that endpoint, THE system SHALL return the original stored response verbatim without re-processing or creating a second record.
- AC-S3: WHEN a `POST` request omits the `Idempotency-Key` header, THE system SHALL reject it with `400` — no mutating financial endpoint accepts an idempotency-free write.

**Error envelope (ADR-0015)**
- AC-S4: WHEN any request is rejected for any reason (validation, authorization, not-found), THE system SHALL respond with `{"error":{"code","message"}}` — never an unstructured error body.

**Pagination (ADR-0015, ADR-0026)**
- AC-S5: WHEN `GET /expenses` is called with no page-size parameter, THE system SHALL default to 50 results; WHEN a page-size parameter exceeds 200, THE system SHALL clamp to 200 rather than reject the request.

**Delegation to `core` (ADR-0001) — no business logic in routes**
- AC-S6: every route handler validates input shape, calls the corresponding `core` function (`createEqualSplitExpense`, `calculateBalances`, `recordSettlement`, `createLedger`, `addMember`), and maps its `ValidationResult` to a response — no split/balance/rounding logic is duplicated or reimplemented in `server`.

## Out of scope
Exact/percentage/weighted/itemized splits, edit/delete, audit log, "simplify debts" — all explicitly Phase 2 per `docs/FEATURES.md`, not this spec. Auth/registration/login itself (ADR-0008, ADR-0030) — assumed already working; this spec starts from "an authenticated user with a valid access token."

## Test plan
- **API/contract test** (ADR-0009 amendment, required before Phase 2): at least one full real-HTTP round-trip — create a ledger, add a member, create an expense, `GET` balances, assert the correct result — using a real HTTP client against the running test server, not an in-process route test alone.
- **Authorization test** per ledger-scoped endpoint (AC-S1): a non-member request is rejected.
- **Idempotency test** (AC-S2, AC-S3): a repeated key returns the cached response; a missing key is rejected.
- **Integration tests** (`ktor-server-test-host` + real H2, per `docs/guidelines/exposed-koin.md`) for each route's happy path and each `core`-level rejection (expense AC-3/4/5, settlement AC-9/10) surfacing correctly through the HTTP layer and error envelope.

## Human-review-required?
Yes — money math (ADR-0017), authorization (ADR-0024), and idempotency (ADR-0023) all apply to this spec simultaneously. Implementation may be delegated; merge requires explicit sign-off on all three, same as the `core` layer was reviewed before merge.
