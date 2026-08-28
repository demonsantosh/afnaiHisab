# ADR-0029: Periodic data-integrity reconciliation (defense in depth)

## Status
Accepted

## Context
`SettlementFactory.kt`'s own comments already note the split-sum-equals-expense-amount invariant "is enforced in `core`'s domain layer... not by a constraint — SQL cannot express it without a trigger." That's the right call for the *write path* `core` controls (ADR-0001). But it means `core`'s validation is the **only** thing standing between the database and a set of `Split` rows that don't actually sum to their `Expense`'s amount — a future bug in a repository, a hand-run data migration, a manual `psql` fix during an incident, or (once it exists) an admin tool could all write directly to the tables and silently violate the invariant with nothing to catch it. Real accounting and ledger systems don't trust the write path alone for exactly this reason — they run periodic reconciliation against their own invariants, independent of whatever wrote the data.

## Decision
A periodic reconciliation check — for now, a query run manually or via a simple scheduled job, not a full system — that verifies, across the whole database: every `Expense`'s `Split`s sum to its `amount`; every ledger's derived balance total nets to what's expected (e.g., the sum of all members' `netBalance` in a ledger should be exactly zero, since money owed by one member is money owed to another — a strong, cheap-to-check global invariant). A violation is reported (logged prominently, or — once any alerting exists — alerted on), not silently ignored or auto-corrected; auto-correcting a detected inconsistency in financial data is its own can of worms and explicitly out of scope here.

## Consequences
- This is defense-in-depth, not a replacement for `core`'s validation — it exists specifically to catch violations `core` didn't cause but also didn't prevent (a bypass, not a bug in the validation logic itself).
- Low urgency while there's no real data and no other write path exists (only `core`'s factories can currently produce these rows) — becomes genuinely important once `server` repositories, any admin/support tooling, or a data migration exist as additional ways data could be written.
- The "sum of all members' netBalance in a ledger is zero" check is a strong, cheap, general-purpose invariant worth implementing first — it would catch a broad class of bugs (not just the split-sum case) without needing per-feature-specific reconciliation logic.
- Not scheduled/automated yet — track as a `docs/STATUS.md` item until it's actually a running job, the same discipline already applied to ADR-0025's backup export.
