# ADR-0003: Phase 1 web UI is React/Next.js, not Compose Multiplatform Web

## Status
Accepted — revisit after Phase 4 (iOS)

## Context
Compose Multiplatform for Web (Kotlin/Wasm) is part of the KMP ecosystem and would maximize "write everything in Kotlin," but it is younger and rougher than the Android/JVM/iOS targets, with less community troubleshooting available. Phase 1's job is to validate the domain model and ship a working MVP fast, not to absorb the least mature part of the ecosystem risk.

## Decision
Phase 1 web UI is React/Next.js, calling the Ktor backend over HTTP like any other client. `core` is not consumed by the web frontend directly (JS can't consume the Kotlin/JVM module without a Kotlin/JS or Wasm target added specifically for that purpose).

## Consequences
- Web UI logic (e.g. form validation mirroring backend rules) may be duplicated in TypeScript rather than reusing `core`'s domain layer. Kept minimal by keeping the web UI thin and trusting the backend as the validation authority.
- Once Android (Phase 3) and iOS (Phase 4) are live and `core` has proven itself across two real platforms, re-evaluate whether migrating web to Compose Multiplatform Web is worth it — at that point it's de-risked by two prior successful integrations instead of being the first.
