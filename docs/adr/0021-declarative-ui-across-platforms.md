# ADR-0021: Declarative UI on every platform, and Android's choice made explicit

## Status
Accepted, amended 2026-08-28 (iOS's "stays undecided" framing superseded by ADR-0032 — the Android decision below is unchanged)

## Context
This project already made this decision without ever stating it as its own principle. ADR-0003 picked React (declarative) for web. ADR-0010 picked MVI as the mobile presentation pattern — an immutable `State` + `Intent` → `reduce` → new `State` shape that presupposes a declarative renderer to consume it; paired with imperative UI (Android Views/XML, UIKit), MVI would need a pile of manual "now go mutate this specific view" glue code it's explicitly designed to avoid. ADR-0010's own text names iOS's undecided choice as "Compose Multiplatform UI or SwiftUI" — both declarative — but never states Android's choice as its own decision, and never says why plain (Android-only) Jetpack Compose isn't the same thing as Compose Multiplatform UI for this project's purposes.

## Decision
**Every platform uses a declarative UI framework**: React (web, ADR-0003), and **Compose Multiplatform UI for Android** (Phase 3) — not plain Android-only Jetpack Compose. The distinction matters: Compose Multiplatform UI is the version capable of sharing actual UI code with iOS, which is exactly what made it possible to decide iOS's framework early instead of waiting for Phase 4 (**ADR-0032**, superseding this ADR's original "stays explicitly undecided until Phase 4" framing) — iOS also uses Compose Multiplatform UI, the same framework as Android.

## Consequences
- Android (Phase 3) scaffolding uses the Compose Multiplatform UI Gradle setup, not the plain Jetpack Compose (Android-only) one — a real, concrete build-configuration difference to get right at Phase 3's start, not something to discover mid-implementation.
- If Phase 4 later picks Compose Multiplatform UI for iOS too, actual composable UI code (not just the MVI state layer) becomes shareable between Android and iOS — a benefit only available because of this ADR's Android-side choice, made now.
- No change to ADR-0003 (web stays React, not Compose Multiplatform Web). Phase 4's iOS framework question, open when this ADR was written, is resolved by ADR-0032.
