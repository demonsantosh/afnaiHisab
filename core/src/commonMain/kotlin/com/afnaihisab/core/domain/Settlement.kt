package com.afnaihisab.core.domain

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A recorded payment between two memberships of the same [Ledger], reducing a balance
 * (`docs/domain-model.md` — Settlement).
 *
 * Invariants (not enforced in Phase 0 — types only): `fromMembershipId != toMembershipId`,
 * `amount > 0`.
 *
 * A settle-up *suggestion* (ADR-0007) is not a Settlement until the user confirms it.
 */
data class Settlement(
    val id: Uuid,
    val ledgerId: Uuid,
    val fromMembershipId: Uuid,
    val toMembershipId: Uuid,
    val amount: MinorUnits,
    val currency: CurrencyCode,
    val note: String? = null,
    val createdAt: Instant,
)
