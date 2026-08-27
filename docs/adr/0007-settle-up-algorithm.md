# ADR-0007: Greedy heap-based debt simplification (not min-flow)

## Status
Accepted

## Context
"Simplify debts" (Phase 2-4 feature, see docs/FEATURES.md) needs an algorithm to minimize settlement transactions in a ledger. True minimum-transaction-count debt simplification is NP-complete (equivalent to a partition/subset-sum problem) — an exact solution doesn't scale and isn't worth building.

## Decision
Compute each member's net balance (owed − owes) across the ledger. Repeatedly match the largest creditor with the largest debtor (two max-heaps, or sort + two-pointer) until all balances zero out. This is the approach Splitwise itself uses in production. Pure function signature: `List<MemberBalance> -> List<Settlement>`, living in `core`'s domain layer so it's usable by backend and every client without reimplementation.

## Consequences
- O(n log n), not provably minimal in the worst case, but matches industry practice and is trivial to unit test exhaustively (see ADR-0009).
- Explicitly rejecting max-flow/Ford-Fulkerson: it's a larger implementation for a guarantee (true minimality) this feature doesn't actually need, and still requires extra heuristics to reach minimal transaction count in practice.
- This function is the single highest-value target for property-based/exhaustive testing in the whole domain module — balances must always net to zero after settlement.
