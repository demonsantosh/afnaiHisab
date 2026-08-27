package com.afnaihisab.core.presentation

/*
 * Marker contracts for the hand-rolled MVI presentation layer (ADR-0010), shared by Android and
 * iOS from Phase 3 onward regardless of which UI framework iOS ends up using.
 *
 *   Intent (sealed) --dispatch--> reduce(state, intent) --> State (immutable)
 *                                        |
 *                                        +--> Effect (one-off: navigation, snackbar)
 *
 * Only the contracts exist in Phase 0. The `MutableStateFlow`-backed store is written in Phase 3
 * against a real screen, and is applied only to screens with genuine state complexity (expense
 * form, settle-up preview) — not dogmatically everywhere.
 */

/** Immutable, complete UI state for one screen. Implementations are `data class`es. */
interface MviState

/** A user or system action dispatched at a screen. Implementations are sealed hierarchies. */
interface MviIntent

/** A one-off event (navigate, show a snackbar) that must not be replayed on state restore. */
interface MviEffect

/**
 * A pure `(state, intent) -> state` function — no I/O, no time, no randomness — so it is unit
 * testable in `commonTest` (ADR-0009) with the same leverage as the domain layer.
 */
fun interface MviReducer<S : MviState, I : MviIntent> {
    fun reduce(
        state: S,
        intent: I,
    ): S
}
