# ADR-0005: Koin for dependency injection

## Status
Accepted

## Context
`core` is consumed by a Ktor backend, Android, and (later) iOS. DI needs to work identically across all three without per-platform wiring. The realistic choices are Koin (service-locator style, runtime), kotlin-inject (compile-time, KSP-based), or manual constructor injection.

## Decision
Koin. It's the established DI choice across KMP + Ktor (dedicated `koin-ktor` integration), has no compiler-plugin/build-time cost, and its ecosystem documentation/troubleshooting surface is larger — which matters directly for a learning project where getting unstuck fast beats compile-time DI safety. kotlin-inject is more type-safe but has a smaller contributor base and rougher Ktor integration today.

## Consequences
- DI errors (missing binding) surface at runtime/startup, not compile time — acceptable trade-off given the ecosystem-maturity reasoning above.
- One Koin module definition per layer (`core`, `server`, each app) rather than per-platform hand-wiring.
- Revisit only if compile-time safety becomes a recurring pain point in practice, not preemptively.
