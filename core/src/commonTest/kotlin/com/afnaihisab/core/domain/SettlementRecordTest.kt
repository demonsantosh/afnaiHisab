package com.afnaihisab.core.domain

import com.afnaihisab.core.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `docs/specs/expense-split-balance.md` AC-13 — a recorded settlement always reports the balance
 * between the two parties before and after it, so it is never ambiguous what was settled or
 * whether anything remains.
 *
 * TDD red phase (ADR-0009): [recordSettlement] is a `TODO()` stub in
 * `core/src/commonMain/.../domain/SettlementFactory.kt`. Every test here must fail (by throwing
 * `NotImplementedError` from that `TODO()`), not fail to compile, until a later pass implements
 * the real composition.
 */
class SettlementRecordTest {
    private val ledgerId = uuid(0)
    private val members = testMembers(n = 2, ledgerId = ledgerId)
    private val payer = members[0] // A
    private val other = members[1] // B

    // A pays a $30.00 expense, split evenly with B: A +1500, B -1500 before any settlement.
    private val expense = testExpense(id = uuid(100), ledgerId = ledgerId, payerMembershipId = payer.id, amount = 3000L)
    private val splits =
        listOf(
            testSplit(id = uuid(101), expenseId = expense.id, membershipId = payer.id, amount = 1500L),
            testSplit(id = uuid(102), expenseId = expense.id, membershipId = other.id, amount = 1500L),
        )

    @Test
    fun `AC-13 a settlement that fully clears the balance reports before as owed and after as zero`() {
        // B (other) pays A (payer) the 1500 they owe — matches this file's worked example
        // (docs/specs/expense-split-balance.md AC-13): before B owes 1500 / A is owed 1500,
        // after both are exactly 0.
        val result =
            recordSettlement(
                members = members,
                expenses = listOf(expense),
                splits = splits,
                existingSettlements = emptyList(),
                ledgerId = ledgerId,
                fromMembershipId = other.id,
                toMembershipId = payer.id,
                amount = 1500L,
                currency = "USD",
                createdAt = FIXED_CREATED_AT,
                id = uuid(200),
            )

        val valid = assertIs<ValidationResult.Valid<SettlementRecord>>(result, "a valid settlement must be accepted")
        val record = valid.value

        assertEquals(other.id, record.settlement.fromMembershipId)
        assertEquals(payer.id, record.settlement.toMembershipId)
        assertEquals(1500L, record.settlement.amount)

        // Before: B (from) owed 1500, A (to) was owed 1500.
        assertEquals(other.id, record.fromBefore.membershipId)
        assertEquals(-1500L, record.fromBefore.netBalance)
        assertEquals(payer.id, record.toBefore.membershipId)
        assertEquals(1500L, record.toBefore.netBalance)

        // After: fully settled, both exactly zero — no rounding residue (AC-7's guarantee holds here too).
        assertEquals(0L, record.fromAfter.netBalance)
        assertEquals(0L, record.toAfter.netBalance)
    }

    @Test
    fun `AC-13 a partial settlement reports what still remains afterward`() {
        // B only pays back 500 of the 1500 they owe.
        val result =
            recordSettlement(
                members = members,
                expenses = listOf(expense),
                splits = splits,
                existingSettlements = emptyList(),
                ledgerId = ledgerId,
                fromMembershipId = other.id,
                toMembershipId = payer.id,
                amount = 500L,
                currency = "USD",
                createdAt = FIXED_CREATED_AT,
                id = uuid(201),
            )

        val valid = assertIs<ValidationResult.Valid<SettlementRecord>>(result, "a valid partial settlement must be accepted")
        val record = valid.value

        assertEquals(-1500L, record.fromBefore.netBalance)
        assertEquals(1500L, record.toBefore.netBalance)
        // 1000 still owed on both sides afterward — nothing silently disappears.
        assertEquals(-1000L, record.fromAfter.netBalance)
        assertEquals(1000L, record.toAfter.netBalance)
    }

    @Test
    fun `AC-13 an invalid settlement is rejected without computing any balances`() {
        val result =
            recordSettlement(
                members = members,
                expenses = listOf(expense),
                splits = splits,
                existingSettlements = emptyList(),
                ledgerId = ledgerId,
                fromMembershipId = other.id,
                toMembershipId = other.id, // AC-9: same membership
                amount = 500L,
                currency = "USD",
                createdAt = FIXED_CREATED_AT,
            )

        val invalid = assertIs<ValidationResult.Invalid>(result, "fromMembershipId == toMembershipId must still be rejected here")
        assertTrue(invalid.errors.any { it.code == SettlementValidationCodes.SAME_MEMBERSHIP })
    }
}
