# ADR-0035: MVI containers extend the multiplatform androidx.lifecycle.ViewModel

## Status
Accepted

## Context
ADR-0010 cites "`androidx.lifecycle-viewmodel` is multiplatform now" as the reason Android and iOS can share the actual state-management layer, not just domain logic — but never explicitly decided whether AfnaiHisab's MVI containers (the classes holding `MutableStateFlow<State>`) actually *extend* that `ViewModel` base class, versus hand-rolling their own `CoroutineScope` lifecycle management. Left ambiguous, this is exactly the kind of thing two different implementers (or the same implementer on two different days) could resolve differently.

## Decision
MVI containers **extend `androidx.lifecycle.ViewModel`** and use its `viewModelScope` for coroutines, rather than managing a hand-rolled `CoroutineScope`. Stable as of 2026 — `lifecycle-viewmodel-compose` compiles in `commonMain` with no experimental flags, and this is now the idiomatic pattern for a CMP app's state holders. This **refines, not replaces** ADR-0010: the reducer function, `Intent`/`State` sealed types, and `Effect` channel all stay exactly as hand-rolled and framework-free as originally decided — only the *container's* lifecycle-scoping mechanism changes, from something we'd have had to build ourselves to the platform-provided one.

This also resolves the Compose Multiplatform "Lifecycle" concern generally, not as a separate decision — `ViewModel`'s scope IS the lifecycle-awareness mechanism (survives Android configuration changes, cancels coroutines when the screen is cleared), so there is no additional lifecycle-handling ADR needed beyond this one.

## Consequences
- A real rough edge this sidesteps entirely, not by luck: on SwiftUI, there's no native `ViewModelStore` equivalent, normally requiring a Swift-bridging layer (and separately, Flow-to-Swift bridging via something like KMP-NativeCoroutines) to use a shared Kotlin ViewModel from native iOS UI. Since ADR-0032 already put iOS on Compose Multiplatform UI rather than SwiftUI, both platforms' UI and ViewModel layers stay entirely in Kotlin — no Swift bridging needed in the direct UI-state path. ADR-0032 and this decision reinforce each other; this is a consequence of that earlier choice, not an independent coincidence.
- Every screen's MVI container has a consistent base shape (`class FooViewModel : ViewModel() { private val _state = MutableStateFlow(...); ... }`) from Phase 3 onward — a concrete, checkable pattern for `kotlin-expert-review` to verify, not just prose guidance.
