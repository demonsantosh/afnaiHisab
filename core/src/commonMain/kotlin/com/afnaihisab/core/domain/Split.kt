package com.afnaihisab.core.domain

import kotlin.uuid.Uuid

/**
 * One member's share of an [Expense] (`docs/domain-model.md` — Split).
 *
 * Invariant (not enforced in Phase 0 — types only): all splits of an expense sum to exactly the
 * expense amount.
 *
 * @property amount this member's share, in minor units — the settled result of applying
 *   [Expense.splitType] plus the rounding-remainder rule, not the raw input.
 * @property shareValue the raw input the share was derived from: a percentage (0-100) for
 *   [SplitType.PERCENTAGE] or a weight ("2 shares") for [SplitType.WEIGHTED]. Null for
 *   [SplitType.EQUAL] and [SplitType.EXACT], where there is no separate input to preserve.
 */
data class Split(
    val id: Uuid,
    val expenseId: Uuid,
    val membershipId: Uuid,
    val amount: MinorUnits,
    val shareValue: Long? = null,
)
