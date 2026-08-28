# ADR-0026: Operational limits, timeouts, and graceful shutdown

## Status
Accepted

## Context
A deeper system-design pass asked what the first review didn't: what happens at the *edges* of normal operation — an unbounded query, a huge request body, a hung connection, a deploy that kills the process mid-request. None of these have a stated policy anywhere. At AfnaiHisab's stated scale (ADR-0022: small, best-effort), none of them need sophisticated handling — but "no policy" and "deliberately simple policy" are different things, and only one of them is legible to a future implementer.

## Decision
- **Pagination page size** (ADR-0015's cursor pagination): default page size 50, maximum 200 — a client cannot request an unbounded result set. Enforced server-side (clamp, don't error, on an out-of-range request) so a buggy or malicious client can't force a multi-thousand-row query.
- **Request body size limit**: cap at a small, generous-for-this-domain size (e.g. 1 MB — an expense or settlement payload is a few hundred bytes; even a large receipt-metadata payload in a later phase won't approach this) — configured at the Ktor engine level, rejecting oversized bodies before they're parsed, not after.
- **Timeouts**: an HTTP client timeout (server-to-any-external-call, though there are none yet in Phase 1), a database query timeout (prevents one runaway query from holding a connection indefinitely against the small Hikari pool — ADR-0019's audit already sized that pool conservatively; an unbounded query timeout defeats that sizing), and Hikari's own connection-acquisition timeout left at a sane default (fail fast with a clear error rather than hang) rather than unconfigured.
- **Graceful shutdown**: the server must handle `SIGTERM` (what Koyeb/most PaaS hosts send before killing a container on redeploy) by finishing in-flight requests and closing the Hikari pool cleanly, not by dying mid-transaction. Ktor's engine shutdown hooks handle this; it needs to be verified as configured, not assumed.

## Consequences
- These are cheap, mostly-configuration decisions to make now, before traffic exists to reveal their absence painfully — exactly the "cheaper to decide before Phase 2's deploy than after" reasoning already used for Gradle's configuration cache (ADR-0019).
- None of this is sophisticated resilience engineering (circuit breakers, bulkheads, backpressure) — deliberately not needed at this project's stated scale (ADR-0022). Revisit only if that scale assumption changes.
- Implementation detail for whoever writes the `server` scaffolding update: consult current Ktor 3.5.x docs for the exact plugin/config API — this ADR states the requirement, not the syntax.
