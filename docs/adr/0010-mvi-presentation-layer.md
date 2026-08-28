# ADR-0010: Lightweight, hand-rolled MVI for the shared presentation layer

## Status
Accepted, amended 2026-08-28 twice: (1) stale cross-reference fixed — ADR-0032 later decided iOS's UI framework; (2) ADR-0035 resolved this ADR's own ambiguity about whether the MVI container extends `androidx.lifecycle.ViewModel`. Neither amendment changes the decision below.

## Context
Phase 3 (Android) and Phase 4 (iOS) need a presentation-layer pattern. MVVM (View binds to a ViewModel's observable properties, multiple methods mutate loosely-coupled state) and MVI (View dispatches Intents, a single reducer computes new immutable State) are the realistic choices. The decision has to be made against the *full* roadmap, not just Phase 3's initial screens — split-type validation (equal/exact/percentage/itemized), settle-up preview (ADR-0007), multi-currency conversion (async, race-prone), Phase 5 offline sync (local-optimistic/pending/conflict states), and Phase 6 budget/report derived state all stress MVVM's loosely-coupled mutation model in ways that get progressively worse, not better, as the app grows. Retrofitting unidirectional flow after MVVM ViewModels are entrenched — right as Phase 5 needs it — would be a presentation-layer rewrite, the same failure mode ADR-0001 exists to prevent at the domain layer.

## Decision
Lightweight, hand-rolled MVI: sealed `Intent`, immutable data-class `State`, `MutableStateFlow<State>` as the single source of truth, `SharedFlow<Effect>` for one-off events (navigation, snackbars), one `reduce(state, intent): state` function per screen. Lives in `core` (a `presentation` package alongside `domain`/`data`/`validation`), not per-platform — `androidx.lifecycle-viewmodel` is multiplatform now, so Android and iOS share the actual state-management layer, not just domain logic. This was written when iOS's UI framework was still undecided between SwiftUI and Compose Multiplatform UI — deliberately so, since the `State`/`Intent` contract is UI-framework-agnostic either way. ADR-0032 has since decided iOS uses Compose Multiplatform UI (the same framework as Android), but the reasoning here stands unchanged — this MVI layer was never contingent on that choice.

Not adopting a framework (MVIKotlin, Orbit, Circuit) up front — hand-rolling keeps `core` dependency-light and, for a learning project, surfaces the actual pattern instead of hiding it behind a library. Not applying MVI uniformly to every screen — trivial/static screens (settings, profile) get a plain state holder, not full Intent/Effect ceremony.

## Consequences
- Every non-trivial screen's state transitions are unit-testable in `commonTest` as pure `reduce` functions, reinforcing ADR-0009's testing philosophy.
- More upfront boilerplate per complex screen than MVVM — accepted cost, paid once, versus a rewrite later.
- Revisit trigger: adopt Orbit or Circuit only if hand-rolled boilerplate becomes a measured pain point during Phase 3, not preemptively.
- Judgment call required per screen: full MVI for stateful/complex screens (expense form, settle-up preview, sync status), plain state holder for trivial ones — avoid dogmatic uniformity.
