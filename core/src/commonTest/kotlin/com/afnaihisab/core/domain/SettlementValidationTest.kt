package com.afnaihisab.core.domain

import com.afnaihisab.core.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `docs/specs/expense-split-balance.md` AC-8..AC-10 — settlement validation.
 *
 * TDD red phase (ADR-0009): [createSettlement] is a `TODO()` stub in
 * `core/src/commonMain/.../domain/SettlementFactory.kt`. Every test here must fail (by throwing
 * `NotImplementedError` from that `TODO()`), not fail to compile, until a later pass implements
 * the real validation.
 */
class SettlementValidationTest {
    private val ledgerId = uuid(0)
    private val members = testMembers(n = 2, ledgerId = ledgerId)
    private val fromMember = members[0]
    private val toMember = members[1]

    @Test
    fun `AC-8 creates a settlement between two distinct memberships and it is reflected in subsequent balances`() {
        val result =
            createSettlement(
                ledgerId = ledgerId,
                fromMembershipId = fromMember.id,
                toMembershipId = toMember.id,
                amount = 500L,
                currency = "USD",
                createdAt = FIXED_CREATED_AT,
                id = uuid(200),
            )

        val valid =
            assertIs<ValidationResult.Valid<Settlement>>(result, "a positive amount between two distinct memberships must be accepted")
        val settlement = valid.value

        assertEquals(ledgerId, settlement.ledgerId)
        assertEquals(fromMember.id, settlement.fromMembershipId)
        assertEquals(toMember.id, settlement.toMembershipId)
        assertEquals(500L, settlement.amount)
        assertEquals("USD", settlement.currency)

        // AC-6/AC-8: the created settlement must actually move subsequent balance calculations.
        // No expenses at all: without the settlement both members are 0; with it, `from` is
        // credited (paid down a debt they didn't have yet, so they go positive) and `to` is
        // debited by the same amount.
        val balances = calculateBalances(members = members, expenses = emptyList(), splits = emptyList(), settlements = listOf(settlement))
        val byMembership = balances.associateBy { it.membershipId }
        assertEquals(500L, byMembership.getValue(fromMember.id).netBalance)
        assertEquals(-500L, byMembership.getValue(toMember.id).netBalance)
    }

    @Test
    fun `AC-9 rejects a settlement from a membership to itself`() {
        val result =
            createSettlement(
                ledgerId = ledgerId,
                fromMembershipId = fromMember.id,
                toMembershipId = fromMember.id,
                amount = 500L,
                currency = "USD",
                createdAt = FIXED_CREATED_AT,
            )

        val invalid = assertIs<ValidationResult.Invalid>(result, "fromMembershipId == toMembershipId must be rejected")
        assertTrue(
            invalid.errors.any { it.code == SettlementValidationCodes.SAME_MEMBERSHIP },
            "expected a ValidationError with code=\"${SettlementValidationCodes.SAME_MEMBERSHIP}\", got ${invalid.errors}",
        )
    }

    @Test
    fun `AC-10 rejects a non-positive settlement amount`() {
        for (badAmount in listOf(0L, -1L, -500L)) {
            val result =
                createSettlement(
                    ledgerId = ledgerId,
                    fromMembershipId = fromMember.id,
                    toMembershipId = toMember.id,
                    amount = badAmount,
                    currency = "USD",
                    createdAt = FIXED_CREATED_AT,
                )

            val invalid = assertIs<ValidationResult.Invalid>(result, "amount=$badAmount must be rejected")
            assertTrue(
                invalid.errors.any { it.field == "amount" && it.code == SettlementValidationCodes.AMOUNT_NOT_POSITIVE },
                "amount=$badAmount: expected a ValidationError(field=\"amount\", code=\"${SettlementValidationCodes.AMOUNT_NOT_POSITIVE}\"), got ${invalid.errors}",
            )
        }
    }
}
