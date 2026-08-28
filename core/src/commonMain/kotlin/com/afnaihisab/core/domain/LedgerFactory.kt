// This file is named after the factory functions it exists for (`createLedger`, `addMember`), not
// after the incidental `LedgerCreationResult` below — detekt's one-class-per-matching-filename rule
// doesn't fit a function-first file (the sibling `*Factory.kt` files declare two types each, so
// only this one trips it).
@file:Suppress("MatchingDeclarationName")

package com.afnaihisab.core.domain

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A newly created [Ledger] together with the single owner [Membership] AC-11 requires.
 */
data class LedgerCreationResult(
    val ledger: Ledger,
    val ownerMembership: Membership,
)

/**
 * Creates a [Ledger] plus exactly one [Membership] with role [MembershipRole.OWNER] for
 * [ownerUserId] (`docs/specs/expense-split-balance.md` AC-11).
 *
 * Unlike the expense/balance/settlement functions in this package, this is plain record creation
 * with no documented rejection path in the spec, so it returns the result directly rather than a
 * [com.afnaihisab.core.validation.ValidationResult].
 *
 * @param ledgerId injected id for the new [Ledger] (not `Uuid.random()` internally) so tests stay
 *   deterministic.
 * @param ownerMembershipId injected id for the owner [Membership].
 *
 * `@Suppress("LongParameterList")`: the two id parameters are injected rather than generated
 * internally so tests stay deterministic.
 */
@Suppress("LongParameterList")
fun createLedger(
    name: String,
    defaultCurrency: CurrencyCode,
    ownerUserId: Uuid,
    createdAt: Instant,
    ledgerId: Uuid = Uuid.random(),
    ownerMembershipId: Uuid = Uuid.random(),
): LedgerCreationResult {
    val ledger =
        Ledger(
            id = ledgerId,
            name = name,
            defaultCurrency = defaultCurrency,
            createdAt = createdAt,
        )
    val ownerMembership =
        Membership(
            id = ownerMembershipId,
            ledgerId = ledgerId,
            userId = ownerUserId,
            role = MembershipRole.OWNER,
            joinedAt = createdAt,
        )
    return LedgerCreationResult(ledger, ownerMembership)
}

/**
 * Adds [newUserId] to [ledger] as a [Membership] with role [MembershipRole.MEMBER]
 * (`docs/specs/expense-split-balance.md` AC-12).
 *
 * The spec's "adds another user by email" is a repository/server-layer lookup (email -> `User`)
 * that happens *before* calling this function — resolving an email to a `userId` is not core
 * domain math and is out of scope here; this function takes the already-resolved [newUserId].
 *
 * @param membershipId injected id for the new [Membership] (not `Uuid.random()` internally) so
 *   tests stay deterministic.
 */
fun addMember(
    ledger: Ledger,
    newUserId: Uuid,
    joinedAt: Instant,
    membershipId: Uuid = Uuid.random(),
): Membership =
    Membership(
        id = membershipId,
        ledgerId = ledger.id,
        userId = newUserId,
        role = MembershipRole.MEMBER,
        joinedAt = joinedAt,
    )
