package com.afnaihisab.core.domain

import com.afnaihisab.core.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `docs/specs/expense-split-balance.md` AC-1..AC-5 — equal-split expense creation.
 *
 * TDD red phase (ADR-0009): [createEqualSplitExpense] is a `TODO()` stub in
 * `core/src/commonMain/.../domain/ExpenseFactory.kt`. Every test here must fail (by throwing
 * `NotImplementedError` from that `TODO()`), not fail to compile, until a later pass implements
 * the real logic.
 */
class ExpenseSplittingTest {
    private val ledgerId = uuid(0)
    private val ledger = testLedger(id = ledgerId, currency = CurrencyCode("USD"))

    // ---- AC-1: one Split per current member, summing exactly to the expense amount ----

    @Test
    fun `AC-1 creates one split per current ledger member summing to the expense amount`() {
        val members = testMembers(n = 3, ledgerId = ledgerId)
        val payer = members[0]

        val result =
            createEqualSplitExpense(
                ledger = ledger,
                members = members,
                payerMembershipId = payer.id,
                amount = MinorUnits(300L),
                currency = CurrencyCode("USD"),
                category = "food",
                date = FIXED_DATE,
                createdAt = FIXED_CREATED_AT,
            )

        val valid =
            assertIs<ValidationResult.Valid<ExpenseWithSplits>>(result, "expected a valid equal split for an amount that divides evenly")
        val (expense, splits) = valid.value

        assertEquals(ledgerId, expense.ledgerId)
        assertEquals(payer.id, expense.payerMembershipId)
        assertEquals(MinorUnits(300L), expense.amount)
        assertEquals(CurrencyCode("USD"), expense.currency)
        assertEquals(SplitType.EQUAL, expense.splitType)
        assertEquals(false, expense.isLocked)

        assertEquals(members.size, splits.size, "expected exactly one split per current ledger member")
        assertEquals(members.map { it.id }.toSet(), splits.map { it.membershipId }.toSet())
        assertEquals(MinorUnits(300L), splits.sumOf { it.amount }, "splits must sum exactly to the expense amount")
        assertTrue(splits.all { it.amount == MinorUnits(100L) }, "amount divides evenly across 3 members, so every split should be 100")
    }

    // ---- AC-2: largest-remainder rounding, ascending-membershipId tiebreak ----
    // members' ids are uuid(1)..uuid(n), already ascending, so "first `remainder` members by id"
    // is simply the first `remainder` entries of `testMembers`.

    private data class RoundingCase(
        val memberCount: Int,
        val amount: MinorUnits,
    ) {
        val base: MinorUnits get() = amount / memberCount
        val remainder: Int get() = (amount % memberCount).value.toInt()
    }

    @Test
    fun `AC-2 allocates the leftover minor units to the lowest membershipIds, sum always exact`() {
        val cases =
            listOf(
                RoundingCase(2, MinorUnits(100L)),
                RoundingCase(2, MinorUnits(101L)),
                RoundingCase(2, MinorUnits(1000L)),
                RoundingCase(3, MinorUnits(100L)),
                RoundingCase(3, MinorUnits(101L)),
                RoundingCase(3, MinorUnits(1000L)),
                RoundingCase(7, MinorUnits(100L)),
                RoundingCase(7, MinorUnits(101L)),
                RoundingCase(7, MinorUnits(1000L)),
            )

        for (case in cases) {
            val members = testMembers(n = case.memberCount, ledgerId = ledgerId)
            val payer = members[0]

            val result =
                createEqualSplitExpense(
                    ledger = ledger,
                    members = members,
                    payerMembershipId = payer.id,
                    amount = case.amount,
                    currency = CurrencyCode("USD"),
                    category = "trip",
                    date = FIXED_DATE,
                    createdAt = FIXED_CREATED_AT,
                )

            val valid =
                assertIs<ValidationResult.Valid<ExpenseWithSplits>>(
                    result,
                    "case n=${case.memberCount} amount=${case.amount}: expected a valid split",
                )
            val splits = valid.value.splits

            assertEquals(
                case.amount,
                splits.sumOf { it.amount },
                "case n=${case.memberCount} amount=${case.amount}: split sum must equal the expense amount exactly",
            )
            assertEquals(case.memberCount, splits.size, "case n=${case.memberCount} amount=${case.amount}: one split per member")

            // Reference allocation: members sorted ascending by membershipId; the first
            // `remainder` of them get `base + 1`, the rest get `base` (all fractional remainders
            // are identical for an equal split, so the tiebreak is what decides every allocation).
            val byMembership = splits.associateBy { it.membershipId }
            val sortedMembers = members.sortedBy { it.id }
            sortedMembers.forEachIndexed { index, member ->
                val expected = if (index < case.remainder) case.base + MinorUnits(1L) else case.base
                val actual = byMembership.getValue(member.id).amount
                assertEquals(
                    expected,
                    actual,
                    "case n=${case.memberCount} amount=${case.amount}: member at ascending-id rank $index " +
                        "(membershipId=${member.id}) expected $expected, got $actual",
                )
            }
        }
    }

    // ---- AC-3: amount <= 0 is rejected, no records created ----

    @Test
    fun `AC-3 rejects a non-positive amount and creates no records`() {
        val members = testMembers(n = 2, ledgerId = ledgerId)

        for (badAmount in listOf(MinorUnits(0L), MinorUnits(-1L), MinorUnits(-500L))) {
            val result =
                createEqualSplitExpense(
                    ledger = ledger,
                    members = members,
                    payerMembershipId = members[0].id,
                    amount = badAmount,
                    currency = CurrencyCode("USD"),
                    category = "food",
                    date = FIXED_DATE,
                    createdAt = FIXED_CREATED_AT,
                )

            val invalid = assertIs<ValidationResult.Invalid>(result, "amount=$badAmount must be rejected")
            assertTrue(
                invalid.errors.any { it.field == "amount" && it.code == ExpenseValidationCodes.AMOUNT_NOT_POSITIVE },
                "amount=$badAmount: expected a ValidationError(field=\"amount\", code=\"${ExpenseValidationCodes.AMOUNT_NOT_POSITIVE}\"), got ${invalid.errors}",
            )
        }
    }

    // ---- AC-4: payer must belong to the target ledger ----

    @Test
    fun `AC-4 rejects a payer that does not belong to the target ledger`() {
        val members = testMembers(n = 2, ledgerId = ledgerId)
        val outsiderMembershipId = uuid(999) // not in `members`

        val result =
            createEqualSplitExpense(
                ledger = ledger,
                members = members,
                payerMembershipId = outsiderMembershipId,
                amount = MinorUnits(100L),
                currency = CurrencyCode("USD"),
                category = "food",
                date = FIXED_DATE,
                createdAt = FIXED_CREATED_AT,
            )

        val invalid = assertIs<ValidationResult.Invalid>(result, "a payer outside the ledger's membership set must be rejected")
        assertTrue(
            invalid.errors.any { it.field == "payerMembershipId" && it.code == ExpenseValidationCodes.PAYER_NOT_MEMBER },
            "expected a ValidationError(field=\"payerMembershipId\", code=\"${ExpenseValidationCodes.PAYER_NOT_MEMBER}\"), got ${invalid.errors}",
        )
    }

    // ---- AC-5: currency must equal the ledger's defaultCurrency ----

    @Test
    fun `AC-5 rejects a currency that differs from the ledger's defaultCurrency`() {
        val members = testMembers(n = 2, ledgerId = ledgerId)

        val result =
            createEqualSplitExpense(
                ledger = ledger, // defaultCurrency = CurrencyCode("USD")
                members = members,
                payerMembershipId = members[0].id,
                amount = MinorUnits(100L),
                currency = CurrencyCode("NPR"),
                category = "food",
                date = FIXED_DATE,
                createdAt = FIXED_CREATED_AT,
            )

        val invalid = assertIs<ValidationResult.Invalid>(result, "a currency mismatching the ledger's defaultCurrency must be rejected")
        assertTrue(
            invalid.errors.any { it.field == "currency" && it.code == ExpenseValidationCodes.CURRENCY_MISMATCH },
            "expected a ValidationError(field=\"currency\", code=\"${ExpenseValidationCodes.CURRENCY_MISMATCH}\"), got ${invalid.errors}",
        )
    }
}
