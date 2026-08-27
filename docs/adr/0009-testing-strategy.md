# ADR-0009: kotlin.test in commonTest as the primary test surface

## Status
Accepted

## Context
ADR-0001 puts all business logic (split math, balance calculation, settle-up) in `core`'s domain layer specifically so it protects backend + Android + iOS from one test suite. That only pays off if the tests are actually written there and not scattered per-platform.

## Decision
- `kotlin.test` + `kotlinx-coroutines-test` in `commonTest` for all domain logic — runs on every target for free, no per-platform duplication.
- `ktor-server-test-host` for backend route/integration tests.
- `MockEngine` for testing `core`'s Ktor-client networking code without a live server.
- Kover for coverage reporting.
- Kotest is optional (richer assertions/property testing) — add only if a specific need arises (e.g. property-based testing for ADR-0007's settle-up function), not adopted wholesale up front.

## Consequences
- Domain logic (especially ADR-0007's debt simplification) gets exhaustive `commonTest` coverage before any UI consumes it — test-first per docs/WORKFLOW.md's `smh:tdd-guide` delegation rule.
- Backend integration tests stay separate from domain unit tests, so a backend-only change doesn't require re-running the full multiplatform test matrix unnecessarily.
- No per-platform (Android/iOS) test duplication for logic that has no platform-specific behavior.
