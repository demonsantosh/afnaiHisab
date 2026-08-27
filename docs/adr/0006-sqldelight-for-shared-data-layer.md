# ADR-0006: SQLDelight for the shared mobile data layer

## Status
Accepted — reconsider if Phase 3 (Android) ships significantly before Phase 4 (iOS) with no near-term iOS plan

## Context
Room 3.0 (2026) added first-class KMP support, closing the gap that used to make SQLDelight the obvious default. Both are now real options for Phase 3's local read-through cache and Phase 5's offline write queue.

## Decision
SQLDelight. It writes plain SQL directly in `commonMain` and generates typesafe Kotlin, which fits ADR-0001's shared-module-first philosophy better than Room's Android-native-first tooling. Phase 4 (iOS) benefits from a query layer already proven multiplatform in Phase 3, rather than inheriting Android-specific assumptions.

## Consequences
- Query files live in `core`, not per-platform — one schema, one generated API for Android and iOS.
- Slightly less mature Android-specific tooling (migrations, Android Studio inspector integration) than Room 3.0 offers.
- This is a Phase 3 decision in practice (no local DB exists before then) — captured now so Phase 3 doesn't re-litigate it, per docs/WORKFLOW.md's anti-re-derivation goal.
