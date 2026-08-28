# ADR-0005: Koin for dependency injection

## Status
Accepted, amended 2026-08-28 (one more alternative considered — the decision is unchanged)

## Amendment (2026-08-28)
A best-practice audit noted Ktor added its own native DI in 3.2.0, not available when this ADR was first written. Considered and rejected: Ktor's built-in DI is server-only — no scoping, no compile-time verification, and critically no reach to Android/iOS — so it can't serve this ADR's actual reason for existing (one DI approach shared across `server` and future mobile clients). Koin remains the right choice for that reason alone, independent of Ktor's native option.

## Context
`core` is consumed by a Ktor backend, Android, and (later) iOS. DI needs to work identically across all three without per-platform wiring. The realistic choices are Koin (service-locator style, runtime), kotlin-inject (compile-time, KSP-based), or manual constructor injection.

## Decision
Koin. It's the established DI choice across KMP + Ktor (dedicated `koin-ktor` integration), has no compiler-plugin/build-time cost, and its ecosystem documentation/troubleshooting surface is larger — which matters directly for a learning project where getting unstuck fast beats compile-time DI safety. kotlin-inject is more type-safe but has a smaller contributor base and rougher Ktor integration today.

## Consequences
- DI errors (missing binding) surface at runtime/startup, not compile time — acceptable trade-off given the ecosystem-maturity reasoning above.
- One Koin module definition per layer (`core`, `server`, each app) rather than per-platform hand-wiring.
- Revisit only if compile-time safety becomes a recurring pain point in practice, not preemptively.
