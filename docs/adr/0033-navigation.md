# ADR-0033: Navigation — androidx.navigation (Navigation Compose Multiplatform)

## Status
Accepted

## Context
Nothing has decided how screens are navigated between in the mobile app (Android/iOS, both Compose Multiplatform UI per ADR-0032). `ADR-0010`'s MVI design already routes navigation as a one-off `Effect` (`SharedFlow<Effect>`), but that's the *signal*, not the mechanism that actually moves between screens, manages a back stack, or handles deep links. Three realistic options: the official `androidx.navigation` (Navigation Compose, now multiplatform), Decompose (component-based, most architecturally powerful), or Voyager (least boilerplate, third-party).

## Decision
**`androidx.navigation` (Navigation Compose Multiplatform).** As of CMP 1.10.0 (Jan 2026), non-Android navigation is no longer experimental — the official library is stable for standard use cases, removing the reason to reach for a third-party alternative. Decompose's component-based philosophy is exactly the kind of framework commitment ADR-0010 already deliberately rejected (MVIKotlin, Orbit, Circuit, for the same reason: this project hand-rolls MVI rather than adopting an opinionated framework up front) — adopting Decompose now for navigation would cut against that established reasoning even though it wouldn't literally conflict with MVI itself. This also matches ADR-0005's Koin-over-kotlin-inject reasoning: prefer the ecosystem-standard, best-documented option over a more powerful bespoke one, especially for a learning project where getting unstuck via community docs matters.

## Consequences
- Navigation `Effect`s emitted from an MVI reducer (ADR-0010) are handled by a screen's Composable via `NavController` calls — the reducer still only *signals* navigation intent, `androidx.navigation` does the actual routing.
- Type-safe route definitions (Navigation Compose's serializable route objects) should be used from the start, not string-based routes — cheap to do correctly now, annoying to retrofit.
- If hand-rolled-MVI-style friction ever appears with this library specifically (unlikely, given it's the official option), Decompose remains the documented fallback — not adopted preemptively.
