package com.afnaihisab.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `docs/specs/expense-split-balance.md` AC-6, AC-7 — balance derivation.
 *
 * TDD red phase (ADR-0009): [calculateBalances] is a `TODO()` stub in
 * `core/src/commonMain/.../domain/BalanceCalculator.kt`. Every test here must fail (by throwing
 * `NotImplementedError` from that `TODO()`), not fail to compile, until a later pass implements
 * the real derivation. These tests build [Expense]/[Split]/[Settlement] fixtures directly rather
 * than going through [createEqualSplitExpense], so a failure here isolates AC-6/AC-7 from AC-1..5.
 */
class BalanceCalculationTest {
    private val ledgerId = uuid(0)

    @Test
    fun `AC-6 derives net balance from splits and settlements, owed minus owed minus settlements`() {
        val members = testMembers(n = 3, ledgerId = ledgerId) // m1 (payer), m2, m3
        val (m1, m2, m3) = members

        // Expense: m1 pays 300, split evenly 100/100/100.
        val expenseId = uuid(100)
        val expense = testExpense(id = expenseId, ledgerId = ledgerId, payerMembershipId = m1.id, amount = MinorUnits(300L))
        val splits =
            listOf(
                testSplit(id = uuid(101), expenseId = expenseId, membershipId = m1.id, amount = MinorUnits(100L)),
                testSplit(id = uuid(102), expenseId = expenseId, membershipId = m2.id, amount = MinorUnits(100L)),
                testSplit(id = uuid(103), expenseId = expenseId, membershipId = m3.id, amount = MinorUnits(100L)),
            )

        // Settlement: m2 pays m1 back 100 (fully settling m2's share).
        val settlements =
            listOf(
                testSettlement(
                    id = uuid(200),
                    ledgerId = ledgerId,
                    fromMembershipId = m2.id,
                    toMembershipId = m1.id,
                    amount = MinorUnits(100L),
                ),
            )

        val balances = calculateBalances(members = members, expenses = listOf(expense), splits = splits, settlements = settlements)

        assertEquals(3, balances.size)
        val byMembership = balances.associateBy { it.membershipId }

        // m1: paid 300, owes their own 100 share, received 100 settlement from m2 -> 300 - 100 - 100 = 100
        assertEquals(MinorUnits(100L), byMembership.getValue(m1.id).netBalance, "m1 should still be owed 100 (from m3)")
        // m2: paid nothing, owes 100, paid a 100 settlement -> 0 - 100 + 100 = 0
        assertEquals(MinorUnits(0L), byMembership.getValue(m2.id).netBalance, "m2 fully settled their 100 share")
        // m3: paid nothing, owes 100, no settlement -> 0 - 100 = -100
        assertEquals(MinorUnits(-100L), byMembership.getValue(m3.id).netBalance, "m3 still owes 100")

        assertEquals(MinorUnits(0L), balances.sumOf { it.netBalance }, "balances must always net to zero across all members")
    }

    @Test
    fun `AC-7 a fully-settled ledger nets to exactly zero for every member, even with AC-2 rounding`() {
        val members = testMembers(n = 3, ledgerId = ledgerId) // m1 (payer), m2, m3
        val (m1, m2, m3) = members

        // Expense of 100 across 3 members does not divide evenly: base=33, remainder=1.
        // Largest-remainder + ascending-membershipId tiebreak (AC-2) gives m1 (lowest id) the
        // extra cent: 34/33/33.
        val expenseId = uuid(100)
        val expense = testExpense(id = expenseId, ledgerId = ledgerId, payerMembershipId = m1.id, amount = MinorUnits(100L))
        val splits =
            listOf(
                testSplit(id = uuid(101), expenseId = expenseId, membershipId = m1.id, amount = MinorUnits(34L)),
                testSplit(id = uuid(102), expenseId = expenseId, membershipId = m2.id, amount = MinorUnits(33L)),
                testSplit(id = uuid(103), expenseId = expenseId, membershipId = m3.id, amount = MinorUnits(33L)),
            )
        assertEquals(MinorUnits(100L), splits.sumOf { it.amount }, "fixture sanity check: splits must sum to the expense amount")

        // Every non-payer settles their exact share with the payer.
        val settlements =
            listOf(
                testSettlement(
                    id = uuid(200),
                    ledgerId = ledgerId,
                    fromMembershipId = m2.id,
                    toMembershipId = m1.id,
                    amount = MinorUnits(33L),
                ),
                testSettlement(
                    id = uuid(201),
                    ledgerId = ledgerId,
                    fromMembershipId = m3.id,
                    toMembershipId = m1.id,
                    amount = MinorUnits(33L),
                ),
            )

        val balances = calculateBalances(members = members, expenses = listOf(expense), splits = splits, settlements = settlements)

        for (balance in balances) {
            assertEquals(
                MinorUnits(0L),
                balance.netBalance,
                "membership ${balance.membershipId} should be exactly 0 after full settlement, no residual cents from AC-2's rounding",
            )
        }
    }
}
