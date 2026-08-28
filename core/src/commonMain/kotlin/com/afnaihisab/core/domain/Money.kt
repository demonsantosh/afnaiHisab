package com.afnaihisab.core.domain

import kotlin.jvm.JvmInline

/**
 * Money is always an integer count of a currency's **minor units** (cents for USD, paisa for NPR,
 * whole yen for JPY — the ISO 4217 currency code determines the exponent).
 *
 * Never `Float`, `Double`, or an implicit-scale decimal: integer arithmetic has no representation
 * error, which is what makes the largest-remainder rounding rule exact by construction
 * (`docs/domain-model.md` — "Critical invariant: money is integer minor units, never float").
 *
 * This is a `@JvmInline value class`, not a `typealias`: it compiles to a bare `Long` wherever the
 * type is statically known (zero allocation, zero runtime cost) while making the compiler reject a
 * stray `Long` — a split count, a timestamp, a `shareValue` — being passed where money is expected.
 * A bare `typealias` gave the invariant a name but no enforcement.
 *
 * Persistence note: there is no Exposed column-type mapping for this type yet (Phase 0 wires
 * Exposed but owns no table objects); the Phase 1 repositories that add them must unwrap/wrap
 * explicitly at that boundary via [value] / `MinorUnits(...)`.
 *
 * @property value the raw signed minor-unit count. Negative is meaningful for a derived
 *   [MemberBalance.netBalance]; stored `amount` fields are validated positive by their factories.
 */
@JvmInline
value class MinorUnits(
    val value: Long,
) : Comparable<MinorUnits> {
    operator fun plus(other: MinorUnits): MinorUnits = MinorUnits(value + other.value)

    operator fun minus(other: MinorUnits): MinorUnits = MinorUnits(value - other.value)

    operator fun unaryMinus(): MinorUnits = MinorUnits(-value)

    /** Equal-share base for splitting an amount across `divisor` members (`docs/FEATURES.md` §a). */
    operator fun div(divisor: Int): MinorUnits = MinorUnits(value / divisor)

    /** Leftover minor units the largest-remainder rule has to allocate. */
    operator fun rem(divisor: Int): MinorUnits = MinorUnits(value % divisor)

    override fun compareTo(other: MinorUnits): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()

    companion object {
        val ZERO: MinorUnits = MinorUnits(0L)
    }
}

/** Sums minor units without leaving the type — the stdlib's `sum()` only knows primitives. */
fun Iterable<MinorUnits>.sum(): MinorUnits {
    var total = MinorUnits.ZERO
    for (element in this) total += element
    return total
}

/** [MinorUnits] overload of the stdlib's numeric `sumOf`, for `splits.sumOf { it.amount }`. */
inline fun <T> Iterable<T>.sumOf(selector: (T) -> MinorUnits): MinorUnits {
    var total = MinorUnits.ZERO
    for (element in this) total += selector(element)
    return total
}

/**
 * ISO 4217 alphabetic currency code, e.g. `"USD"`, `"NPR"`.
 *
 * A `@JvmInline value class` rather than a `typealias String` for the same reason as [MinorUnits]:
 * it costs nothing at runtime and stops a name, a category, or a note from being passed where a
 * currency is expected. Deliberately unvalidated for now — Phase 1 only ever compares a code to
 * [Ledger.defaultCurrency] (spec AC-5); a real ISO 4217 check belongs with Phase 2's conversion.
 *
 * Persistence note: as with [MinorUnits], a future Exposed mapping must unwrap [value] explicitly
 * at the column boundary.
 */
@JvmInline
value class CurrencyCode(
    val value: String,
) {
    override fun toString(): String = value
}
