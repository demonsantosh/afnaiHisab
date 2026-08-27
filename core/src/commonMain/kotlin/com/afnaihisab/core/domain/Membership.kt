package com.afnaihisab.core.domain

import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A member's role in a [Ledger]. */
enum class MembershipRole {
    OWNER,
    MEMBER,
}

/**
 * A [User]'s membership in a [Ledger] (`docs/domain-model.md` — Membership).
 *
 * Invariants (not enforced in Phase 0 — types only):
 * - `(ledgerId, userId)` is unique.
 * - A ledger always has at least one [MembershipRole.OWNER]; removing the last one is rejected.
 *
 * Expenses, splits and settlements reference *memberships*, not users, so a ledger's math stays
 * correct when a user is anonymized rather than hard-deleted (ADR-0014).
 */
data class Membership(
    val id: Uuid,
    val ledgerId: Uuid,
    val userId: Uuid,
    val role: MembershipRole,
    val joinedAt: Instant,
)
