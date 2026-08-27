package com.afnaihisab.core.domain

/**
 * Money is always an integer count of a currency's **minor units** (cents for USD, paisa for NPR,
 * whole yen for JPY — the ISO 4217 currency code determines the exponent).
 *
 * Never `Float`, `Double`, or an implicit-scale decimal: integer arithmetic has no representation
 * error, which is what makes the largest-remainder rounding rule exact by construction
 * (`docs/domain-model.md` — "Critical invariant: money is integer minor units, never float").
 *
 * This is a `typealias`, not a value class, so it stays a plain `Long` at every boundary
 * (JSON, SQL, Kotlin/Native) while still naming the invariant at every use site.
 */
typealias MinorUnits = Long

/** ISO 4217 alphabetic currency code, e.g. `"USD"`, `"NPR"`. */
typealias CurrencyCode = String
