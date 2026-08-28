# ADR-0009: kotlin.test in commonTest as the primary test surface, and which other test types are essential

## Status
Accepted, amended 2026-08-28 (categorizes essential test types beyond unit tests — the unit-test decision itself is unchanged)

## Context
ADR-0001 puts all business logic (split math, balance calculation, settle-up) in `core`'s domain layer specifically so it protects backend + Android + iOS from one test suite. That only pays off if the tests are actually written there and not scattered per-platform. Unit tests alone, though, don't catch everything this project now explicitly requires: ADR-0023's idempotency behavior, ADR-0024's authorization checks, and the actual HTTP contract multiple future clients (web, then mobile) will depend on are all things a unit test — by definition testing one function in isolation — structurally cannot verify.

## Decision
**Unit tests** (unchanged): `kotlin.test` + `kotlinx-coroutines-test` in `commonTest` for all domain logic — runs on every target for free, no per-platform duplication.

**Also required, from early in development, not deferred:**
- **Integration tests** (real adjacent dependency, one layer, in-process) — `ktor-server-test-host` for routes, a real H2 instance for repositories (already the pattern: `MigrationTest`, `HealthRouteTest`, `CorsTest`). Continue this for every new repository/route, not just the ones that exist today.
- **API/contract tests** — a real HTTP round-trip (client → server → response), not an in-process route test. This is what actually protects the API contract multiple clients (web now, mobile in Phase 3/4) depend on; an in-process route test can pass while the real serialized wire format is subtly wrong. At least one such test is required before Phase 2 ships (`docs/PLAN.md`'s Phase 2 "done when").
- **Authorization tests** (ADR-0024) — for every ledger-scoped route, an explicit test that a non-member is rejected. This is a *negative* test category (proving access is denied), not just proving the happy path works, and it's exactly the kind of check a code reviewer's eye can miss.
- **Idempotency tests** (ADR-0023) — an explicit test that a repeated `Idempotency-Key` returns the original response rather than creating a second record.
- **Property-based tests for two specific functions**: ADR-0007's settle-up/debt-simplification algorithm and the largest-remainder rounding rule (`ExpenseFactory.kt`). These are pure, mathematically-defined functions where "for any input, this invariant holds" (settlements always net exactly to zero; splits always sum exactly to the expense amount) is a stronger and cheaper check than enumerating example cases by hand — upgraded here from ADR-0009's original "optional, add only if needed" framing specifically for these two functions, given how central they are to financial correctness. Kotest (or a lighter alternative) is the tool; still not adopted project-wide.
- **E2E tests** (Playwright, ADR-0019) — become essential the moment a real UI flow exists (Phase 1 web onward), not before.

**Deliberately deferred** (per ADR-0022's small-scale NFRs — not gaps, explicit non-goals for now): load/performance testing, mutation testing, visual/snapshot regression testing, concurrency/race-condition load tests beyond the design-level argument already made in `docs/ARCHITECTURE.md`'s "System design" section.

- Kover for coverage reporting (unchanged).

## Consequences
- Domain logic (especially ADR-0007's debt simplification) gets exhaustive `commonTest` coverage before any UI consumes it — test-first per docs/WORKFLOW.md's `smh:tdd-guide` delegation rule.
- Backend integration tests stay separate from domain unit tests, so a backend-only change doesn't require re-running the full multiplatform test matrix unnecessarily.
- No per-platform (Android/iOS) test duplication for logic that has no platform-specific behavior.
- Authorization and idempotency tests are not optional hardening added later — they ship in the same commit as the route/feature they guard, the same discipline already applied to TDD's red-then-green pattern.
