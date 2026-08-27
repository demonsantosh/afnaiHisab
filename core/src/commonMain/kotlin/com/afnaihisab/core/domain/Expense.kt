package com.afnaihisab.core.domain

import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * How an [Expense]'s amount is divided across [Split]s.
 *
 * Phase 1 implements [EQUAL] only; the rest are Phase 2 (`docs/FEATURES.md` §b).
 */
enum class SplitType {
    EQUAL,
    EXACT,
    PERCENTAGE,
    WEIGHTED,
    ITEMIZED,
}

/**
 * An expense recorded against a [Ledger] (`docs/domain-model.md` — Expense).
 *
 * Invariants (not enforced in Phase 0 — types only):
 * - `amount > 0`.
 * - `splits.sumOf { it.amount } == amount` exactly, always. The largest-remainder rounding rule
 *   (`docs/FEATURES.md` §a) exists to guarantee this for non-divisible amounts.
 * - Phase 1: [currency] must equal the ledger's `defaultCurrency`; conversion is Phase 2.
 *
 * @property payerMembershipId the membership that actually paid, not necessarily a split participant.
 * @property date the expense's real-world date, deliberately distinct from [createdAt].
 * @property isLocked Phase 2 — set once the expense is fully settled.
 */
data class Expense(
    val id: Uuid,
    val ledgerId: Uuid,
    val payerMembershipId: Uuid,
    val amount: MinorUnits,
    val currency: CurrencyCode,
    val category: String,
    val note: String? = null,
    val date: LocalDate,
    val createdAt: Instant,
    val splitType: SplitType = SplitType.EQUAL,
    val isLocked: Boolean = false,
)
