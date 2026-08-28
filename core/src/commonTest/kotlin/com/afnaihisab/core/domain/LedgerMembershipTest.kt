package com.afnaihisab.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `docs/specs/expense-split-balance.md` AC-11, AC-12 — ledger/membership creation (the
 * prerequisite for the expense/balance/settlement ACs above).
 *
 * TDD red phase (ADR-0009): [createLedger] and [addMember] are `TODO()` stubs in
 * `core/src/commonMain/.../domain/LedgerFactory.kt`. Every test here must fail (by throwing
 * `NotImplementedError` from that `TODO()`), not fail to compile, until a later pass implements
 * the real logic.
 */
class LedgerMembershipTest {
    @Test
    fun `AC-11 creating a ledger creates exactly one OWNER membership for its creator`() {
        val ownerUserId = uuid(1)

        val result =
            createLedger(
                name = "Trip to Pokhara",
                defaultCurrency = "USD",
                ownerUserId = ownerUserId,
                createdAt = FIXED_CREATED_AT,
                ledgerId = uuid(10),
                ownerMembershipId = uuid(20),
            )

        assertEquals(uuid(10), result.ledger.id)
        assertEquals("Trip to Pokhara", result.ledger.name)
        assertEquals("USD", result.ledger.defaultCurrency)

        assertEquals(uuid(20), result.ownerMembership.id)
        assertEquals(result.ledger.id, result.ownerMembership.ledgerId)
        assertEquals(ownerUserId, result.ownerMembership.userId)
        assertEquals(MembershipRole.OWNER, result.ownerMembership.role)
    }

    @Test
    fun `AC-12 adding a member to a ledger creates a MEMBER membership for that user`() {
        val ledger = testLedger(id = uuid(10), currency = "USD")
        val invitedUserId = uuid(2)

        val membership =
            addMember(
                ledger = ledger,
                newUserId = invitedUserId,
                joinedAt = FIXED_JOINED_AT,
                membershipId = uuid(21),
            )

        assertEquals(uuid(21), membership.id)
        assertEquals(ledger.id, membership.ledgerId)
        assertEquals(invitedUserId, membership.userId)
        assertEquals(MembershipRole.MEMBER, membership.role)
        assertEquals(FIXED_JOINED_AT, membership.joinedAt)
    }
}
