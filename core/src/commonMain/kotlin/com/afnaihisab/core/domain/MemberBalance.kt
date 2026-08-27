package com.afnaihisab.core.domain

import kotlin.uuid.Uuid

/**
 * A member's net position in a ledger — **derived, never stored** (`docs/PLAN.md` §3).
 *
 * Recomputed on read from [Expense] + [Split] + [Settlement]; there is deliberately no balance
 * column anywhere in the schema, which is what keeps Phase 2's expense edit/delete correct by
 * construction instead of requiring manual balance patching.
 *
 * The computation itself is Phase 1 work and lives in this package, exhaustively tested in
 * `commonTest` before any UI consumes it (ADR-0009); it is a human-review lane (ADR-0017).
 *
 * @property netBalance positive = this member is owed money; negative = this member owes.
 */
data class MemberBalance(
    val membershipId: Uuid,
    val netBalance: MinorUnits,
)
