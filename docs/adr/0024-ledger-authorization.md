# ADR-0024: Ledger membership authorization as an explicit, enforced rule

## Status
Accepted

## Context
ADR-0008/0011/0013 establish *authentication* (proving who a user is) thoroughly. Nothing establishes *authorization* (what an authenticated user is allowed to see or do) as an explicit, testable rule. Without one, a route implementation bug — e.g. a route that looks up an expense by id without also checking the requesting user belongs to that expense's ledger — becomes an IDOR (Insecure Direct Object Reference, OWASP Top 10) vulnerability: User A could read or modify User B's private ledger data just by guessing/incrementing an id. This is exactly the kind of gap that's invisible in a happy-path review and only shows up when someone (or something) actually tries the wrong id.

## Decision
**Every `server` route that reads or writes ledger-scoped data (expenses, splits, settlements, memberships) must verify the authenticated user has a `Membership` in the target ledger before performing the operation** — resolved via `core`'s `Membership` data, checked in `server` before the request reaches any repository call. No route is exempt because "it's probably fine" — this check is as mechanical and non-negotiable as the CORS allow-list or the error envelope.

This check is added to ADR-0017's human-review-required lanes (alongside auth/token handling, money math, deletion/anonymization, and the audit log) — a missing or incorrect authorization check is exactly the kind of silent, high-severity bug that lane exists to catch before merge.

## Consequences
- Every future route implementation includes an explicit membership check as part of "the route is done," not an afterthought — `docs/guidelines/exposed-koin.md`'s repository-per-layer pattern should expose a cheap way to answer "is user X a member of ledger Y" for this to be checked consistently rather than reimplemented per route.
- Test coverage for every ledger-scoped route should include an explicit "a non-member is rejected" case — the same discipline already applied to `core`'s domain tests (one test per acceptance criterion), extended to this cross-cutting rule.
- `kotlin-expert-review` (`.claude/skills/kotlin-expert-review/SKILL.md`) should specifically check for this when reviewing any new route — added to that skill's review checklist.
