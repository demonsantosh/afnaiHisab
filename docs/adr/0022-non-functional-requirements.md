# ADR-0022: Non-functional requirements, stated explicitly

## Status
Accepted

## Context
A system-design review surfaced the most basic omission possible: nothing in this project states what scale, availability, or consistency it's actually designed for. Every architectural choice made so far (single Ktor instance, single Postgres, no caching layer, no message queue) is *correct* — but correct *for* an assumption that was never written down, which means a future reader (or agent) can't tell whether it was a deliberate scope decision or an oversight.

## Decision
- **Scale**: designed for a personal/small-group app — tens to low hundreds of users, per-ledger expense counts in the hundreds to low thousands over its lifetime. Not designed for, and not prematurely engineered toward, "web scale."
- **Availability**: best-effort, no formal SLA. Acceptable downtime during deploys/restarts. This is what justifies a single Ktor instance with no redundancy (ADR-0018's staging target, Koyeb free tier, is a single instance by construction).
- **Consistency**: **strong consistency within a ledger**, non-negotiable — a balance must never be transiently wrong in a way a user could act on incorrectly. This is the actual reason the project stays on a single relational Postgres instance with ACID transactions rather than any eventually-consistent store; it is also why Phase 5's offline-first work (ADR-0002) is a deliberately separate, later phase with its own conflict-resolution design, not a default assumption.
- **Latency**: interactive-app feel (sub-second perceived response), no hard numeric SLA — appropriate for a CRUD/forms app, not a real-time system.

## Consequences
- Every existing architectural choice (ADR-0001's monolith, no caching/queue layer, single-instance staging) is now traceable to a stated requirement instead of an implicit assumption — a future scale change (e.g. real multi-tenant production use) should revisit this ADR first, not silently violate it.
- Explicitly not designed for: high write throughput, multi-region deployment, horizontal scaling, sub-100ms latency guarantees. Adding any of these later is a deliberate re-scoping, not a bug fix.
