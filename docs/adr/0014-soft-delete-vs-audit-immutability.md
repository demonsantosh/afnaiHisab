# ADR-0014: Anonymize, don't hard-delete, for GDPR requests against an audited ledger

## Status
Accepted — decided now, before Phase 6's double-entry model ships, per the reasoning below

## Context
ADR-0012's audit log (Phase 2+) creates a genuine tension: an immutable/visible history is exactly what makes a shared ledger trustworthy (ADR-0012's own justification), but a GDPR-style "right to be forgotten" request conflicts with immutability — you can't honor both "nothing was silently erased" and "erase my data" with the same mechanism. This gets structurally harder to retrofit once Phase 6 generalizes to a double-entry model (Account/Transaction/Entry), where entries are even more load-bearing for other members' correctness. Deciding the resolution now avoids designing Phase 6's ledger around an assumption that has to be reversed later.

## Decision
On a deletion request, **anonymize, don't hard-delete**: redact personally-identifying fields (name, email) on the requesting user's records, but preserve the numeric/structural entry (amounts, splits, transaction links) so other members' shared-ledger balances and audit history remain mathematically correct. A hard-delete that removed a User's participation in a shared Expense would corrupt every other member's balance history — anonymization is not a compliance-only choice, it's also the only option that keeps the domain model correct.

Explicitly rejected: a blockchain-style cryptographically-immutable ledger. It solves a problem this app doesn't have (adversarial multi-party trust with no central authority) and actively worsens the GDPR tension above — a plain audit-log table (ADR-0012) already gets the real benefit (visible history) without an un-redactable structure.

## Consequences
- User deletion is a distinct, deliberate operation from account deactivation — implement it as "anonymize + retain structure," not as a cascading `DELETE`.
- Applies uniformly from Phase 2's audit log through Phase 6's double-entry generalization — one policy, not two.
- No EU-specific compliance work is in scope before there's an actual EU user base; this ADR fixes the *mechanism* now so it doesn't have to be re-architected under deadline pressure later, not a claim that full GDPR compliance is complete.
