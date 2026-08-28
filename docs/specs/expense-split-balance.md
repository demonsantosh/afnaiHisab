# Spec: Expense, equal split, balance calculation, settlement

Per ADR-0016. Implements `docs/FEATURES.md` §(a) — Phase 1 MVP. This is the money-math lane ADR-0017 flags for explicit human review before merge — draft implementation is fine to delegate, the merge itself is not.

## Summary
A member of a Ledger (personal or shared, ADR-0004) records an Expense with an equal split across all current members, the system derives each member's balance from Expenses + Settlements (never stored, `docs/PLAN.md` §3), and a member can record a Settlement against another member to reduce a balance.

## Acceptance criteria (EARS format)

**Adding an expense**
- AC-1: WHEN a member creates an Expense with a positive `amount`, a `payerMembershipId` belonging to the same Ledger, and `splitType = EQUAL`, THE system SHALL create one `Split` per current Ledger member with `amount` fields summing exactly to the Expense's `amount`.
- AC-2: WHEN an Expense's `amount` does not divide evenly across the Ledger's member count, THE system SHALL allocate the leftover minor units (cents) to the splits with the largest fractional remainder, breaking ties by ascending `membershipId`, per the rounding-remainder rule in `docs/FEATURES.md` §(a).
- AC-3: WHEN an Expense is submitted with `amount <= 0`, THE system SHALL reject it with the standard error envelope (ADR-0015) and create no records.
- AC-4: WHEN an Expense is submitted with a `payerMembershipId` that does not belong to the target Ledger, THE system SHALL reject it and create no records.
- AC-5: WHEN an Expense is submitted with `currency` different from the Ledger's `defaultCurrency`, THE system SHALL reject it (Phase 1 has no conversion — that's `docs/FEATURES.md` §(b)).

**Balance calculation**
- AC-6: WHEN balances are requested for a Ledger, THE system SHALL compute each member's net balance as (sum of Split amounts where they are owed, i.e. paid on others' behalf) minus (sum of Split amounts they owe) minus (net Settlements already paid/received), derived on read — never from a stored balance column.
- AC-7: WHEN all of a Ledger's Expenses are fully settled, THE system SHALL report every member's balance as exactly zero — no residual cents from AC-2's rounding.

**Settlement**
- AC-8: WHEN a member records a Settlement with a positive `amount` between two distinct memberships of the same Ledger, THE system SHALL create the Settlement and reflect it in subsequent balance calculations (AC-6).
- AC-9: WHEN a Settlement is submitted with `fromMembershipId == toMembershipId`, THE system SHALL reject it.
- AC-10: WHEN a Settlement is submitted with `amount <= 0`, THE system SHALL reject it.
- AC-13: WHEN a Settlement is successfully recorded, THE system SHALL also report the balance between the two parties immediately before and immediately after the settlement, so what was settled — and whether anything remains — is never ambiguous to whoever recorded or views it. This is a use-case-level composition (`recordSettlement`), not a change to `createSettlement`'s validation responsibility (AC-8..AC-10) or to the `Settlement` record itself — no new stored field, no linkage to specific expenses (that would be a bigger, Splitwise-diverging design change this spec explicitly does not make).

**Ledger/membership (prerequisite for the above)**
- AC-11: WHEN a user creates a Ledger, THE system SHALL create exactly one Membership with role `OWNER` for that user.
- AC-12: WHEN a Ledger's owner adds another user by email, THE system SHALL create a Membership with role `MEMBER` for that user on that Ledger.

## Out of scope (see `docs/FEATURES.md` §(b) for when)
- Exact/percentage/weighted/itemized splits — equal only, this spec.
- Expense edit/delete, audit log (ADR-0012) — Phase 1 is append-only.
- Multi-currency conversion, recurring expenses, receipts, notifications.
- "Simplify debts" (ADR-0007) — a distinct feature/spec, not part of this one.

## Test plan
- `commonTest` in `core`: one test per AC above, plus specifically an exhaustive/parameterized test of AC-2's rounding rule across member counts (2, 3, 7 — anything that doesn't divide evenly) and a property-style check that split sums always equal the expense amount for randomized (seeded, not `Math.random`) inputs.
- `ktor-server-test-host` in `server`: request validation (AC-3, 4, 5, 9, 10) return the correct error envelope and status code; a full create-expense → get-balances → record-settlement → get-balances round trip.

## Human-review-required?
**Yes** — this is the balance/settle-up money-math lane (ADR-0017). The spec and tests can be drafted by an agent; the merge itself needs your explicit sign-off, specifically on AC-2's rounding rule and AC-6's derivation logic.
