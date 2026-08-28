# ADR-0012: Visible audit log for shared-ledger mutations

## Status
Accepted — applies starting Phase 2, not Phase 1. Amended 2026-08-28 (scope widened to Membership).

## Context
Phase 1 is append-only by construction: expenses and settlements are only created, never edited or deleted (no edit/delete capability exists in tier (a) of `docs/FEATURES.md`). Phase 2 introduces expense edit/delete with balance recalculation. Once a shared-ledger expense can be mutated by any member, "who changed what, and when" becomes a trust requirement, not a nicety — a group member disputing an edited expense needs an answer, and silent edits to shared financial data are a trust failure in any real accounting tool.

A deeper system-design pass (2026-08-28) noted this ADR's original scope — Expense and Settlement only — misses another mutation that matters just as much for trust: **Membership changes**. A member being silently removed from a shared ledger (or a role silently changed from OWNER to MEMBER) is exactly the same category of "who did this and when" dispute as an edited expense, and Membership rows are already mutable from Phase 1 (AC-11/AC-12 create them; nothing in the spec prevented future removal/role-change logic from mutating them without a trail).

## Decision
From Phase 2 onward, every mutation to an Expense, Settlement, **or Membership** (added, removed, role changed) writes an append-only `AuditLogEntry` (who, what changed, old value, new value, timestamp) alongside the mutable record. Not full event-sourcing (the mutable record is still the source of truth for current state) — just a visible, queryable trail. This is a lightweight mechanism, not a redesign of the domain model.

## Consequences
- Phase 2 scope includes the audit log alongside edit/delete — they ship together, not audit-log-as-an-afterthought.
- This audit log is the same mechanism ADR-0014 (soft-delete/anonymization for GDPR) builds on — read that ADR before implementing deletion against audited records.
- Phase 6's double-entry generalization (Account/Transaction/Entry) inherits this pattern rather than reinventing it — transactions get the same audit trail transactions/entries would need anyway in a real accounting tool.
- No audit log needed for Phase 1 — there's nothing to audit yet. Don't build this early; it has no value until Phase 2's mutability exists.
