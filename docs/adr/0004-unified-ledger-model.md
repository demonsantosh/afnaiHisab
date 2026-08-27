# ADR-0004: Personal and shared expenses are one Ledger entity

## Status
Accepted

## Context
The product needs to support personal expense tracking now and evolve into a multipurpose accounting app later, while starting as a Splitwise clone (which is inherently group/shared-first). If "personal" and "shared" are modeled as separate entities (or personal is bolted on as a special case of Group), the accounting-app pivot in Phase 6 would require reconciling two divergent data models.

## Decision
A single `Ledger` entity represents both cases: a personal ledger has exactly one Member (the owner), a shared ledger has N members. Expense, Split, and Settlement all reference Ledger uniformly — no branching logic based on member count anywhere in `core`'s domain layer.

## Consequences
- Balance calculation, expense CRUD, and settlement logic are written once and apply identically to personal and shared ledgers.
- Phase 6's double-entry generalization (Account/Transaction/Entry) has one model to migrate, not two.
- UI layers (web, then mobile) are free to present personal vs. shared ledgers differently, but that's presentation-only — the domain model underneath must never fork on ledger type.
