# ADR-0021: Declarative UI on every platform, and Android's choice made explicit

## Status
Accepted

## Context
This project already made this decision without ever stating it as its own principle. ADR-0003 picked React (declarative) for web. ADR-0010 picked MVI as the mobile presentation pattern — an immutable `State` + `Intent` → `reduce` → new `State` shape that presupposes a declarative renderer to consume it; paired with imperative UI (Android Views/XML, UIKit), MVI would need a pile of manual "now go mutate this specific view" glue code it's explicitly designed to avoid. ADR-0010's own text names iOS's undecided choice as "Compose Multiplatform UI or SwiftUI" — both declarative — but never states Android's choice as its own decision, and never says why plain (Android-only) Jetpack Compose isn't the same thing as Compose Multiplatform UI for this project's purposes.

## Decision
**Every platform uses a declarative UI framework**: React (web, ADR-0003), and **Compose Multiplatform UI for Android** (Phase 3) — not plain Android-only Jetpack Compose. The distinction matters: Compose Multiplatform UI is the version capable of sharing actual UI code with iOS later, which is what keeps ADR-0010's Phase 4 choice ("Compose Multiplatform UI or SwiftUI") a real option rather than a foregone conclusion toward SwiftUI by default. iOS's UI framework stays explicitly undecided until Phase 4, per ADR-0010/`ARCHITECTURE.md`'s existing "Platform look-and-feel" note — this ADR does not resolve that, it only settles that whichever it is, it's declarative.

## Consequences
- Android (Phase 3) scaffolding uses the Compose Multiplatform UI Gradle setup, not the plain Jetpack Compose (Android-only) one — a real, concrete build-configuration difference to get right at Phase 3's start, not something to discover mid-implementation.
- If Phase 4 later picks Compose Multiplatform UI for iOS too, actual composable UI code (not just the MVI state layer) becomes shareable between Android and iOS — a benefit only available because of this ADR's Android-side choice, made now.
- No change to ADR-0003 (web stays React, not Compose Multiplatform Web) or to Phase 4's genuinely open SwiftUI-vs-Compose-Multiplatform-UI decision.
