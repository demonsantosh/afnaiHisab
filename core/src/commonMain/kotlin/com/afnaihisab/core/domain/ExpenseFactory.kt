package com.afnaihisab.core.domain

import com.afnaihisab.core.validation.ValidationError
import com.afnaihisab.core.validation.ValidationResult
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Stable, machine-readable [com.afnaihisab.core.validation.ValidationError.code] values produced by
 * [createEqualSplitExpense] (`docs/specs/expense-split-balance.md` AC-3, AC-4, AC-5). Exposed as
 * constants so tests and the eventual server-layer error-envelope mapping (ADR-0015) share one
 * source of truth instead of duplicating string literals.
 */
object ExpenseValidationCodes {
    /** AC-3: `amount <= 0`. */
    const val AMOUNT_NOT_POSITIVE: String = "amount_not_positive"

    /** AC-4: `payerMembershipId` is not a current member of the target ledger. */
    const val PAYER_NOT_MEMBER: String = "payer_not_member"

    /** AC-5: `currency` differs from the ledger's `defaultCurrency`. */
    const val CURRENCY_MISMATCH: String = "currency_mismatch"
}

/**
 * An [Expense] together with the [Split]s its equal-split allocation produced
 * (`docs/specs/expense-split-balance.md` AC-1, AC-2). Not a persisted entity — just the return
 * shape of [createEqualSplitExpense]; the caller is responsible for storing both.
 */
data class ExpenseWithSplits(
    val expense: Expense,
    val splits: List<Split>,
)

/**
 * Creates an [Expense] with an equal split across [members]
 * (`docs/specs/expense-split-balance.md` AC-1..AC-5).
 *
 * This is a human-review lane (ADR-0017) — the rounding rule below is implemented exactly as
 * specified, not approximated.
 *
 * Behavior:
 * - AC-1: exactly one [Split] per entry in [members], `splits.sumOf { it.amount } == amount`.
 * - AC-2 (largest-remainder method, `docs/FEATURES.md` §a): let `n = members.size`,
 *   `base = amount / n`, `remainder = (amount % n)`. Every member's split starts at `base`; the
 *   `remainder` members with the *largest fractional remainder* each get one extra minor unit.
 *   Because every member has an identical equal share, every fractional remainder is identical —
 *   so the tie always applies, and is broken by **ascending `membershipId`**: [members] sorted by
 *   `id` ascending, the first `remainder` of them get `base + 1`, the rest get `base`.
 * - AC-3: `amount <= 0` -> `Invalid` with one error, `field = "amount"`,
 *   `code = `[ExpenseValidationCodes.AMOUNT_NOT_POSITIVE]`, and no [Expense]/[Split] is
 *   constructed.
 * - AC-4: `payerMembershipId` not present in [members] (matched by [Membership.id], and that
 *   member's [Membership.ledgerId] must equal `ledger.id`) -> `Invalid`,
 *   `field = "payerMembershipId"`, `code = `[ExpenseValidationCodes.PAYER_NOT_MEMBER]`.
 * - AC-5: `currency != ledger.defaultCurrency` -> `Invalid`, `field = "currency"`,
 *   `code = `[ExpenseValidationCodes.CURRENCY_MISMATCH]`. Phase 1 has no conversion.
 * - Multiple violations are reported together (`ValidationResult.Invalid.errors` is a list) —
 *   validation runs to completion before any rejection is returned.
 *
 * @param members the ledger's *current* members — determines `n` for AC-2 and is what AC-4
 *   validates [payerMembershipId] against.
 * @param newId supplies each generated id: the [Expense]'s id first, then one per [Split] in the
 *   same order as [members] — injected (rather than calling `Uuid.random()` internally) so tests
 *   can assert on exact ids with deterministic input, per this project's ban on non-deterministic
 *   tests (`docs/adr/0009-testing-strategy.md`).
 *
 * `@Suppress("LongParameterList")`: an expense genuinely carries this much data, and `newId` is
 * injected rather than called internally so tests stay deterministic; every call site uses named
 * arguments.
 */
@Suppress("LongParameterList")
fun createEqualSplitExpense(
    ledger: Ledger,
    members: List<Membership>,
    payerMembershipId: Uuid,
    amount: MinorUnits,
    currency: CurrencyCode,
    category: String,
    note: String? = null,
    date: LocalDate,
    createdAt: Instant,
    newId: () -> Uuid = { Uuid.random() },
): ValidationResult<ExpenseWithSplits> {
    val errors = mutableListOf<ValidationError>()

    if (amount.value <= 0L) {
        errors +=
            ValidationError(
                field = "amount",
                code = ExpenseValidationCodes.AMOUNT_NOT_POSITIVE,
                message = "amount must be positive",
            )
    }

    val payer = members.find { it.id == payerMembershipId }
    if (payer == null || payer.ledgerId != ledger.id) {
        errors +=
            ValidationError(
                field = "payerMembershipId",
                code = ExpenseValidationCodes.PAYER_NOT_MEMBER,
                message = "payerMembershipId must belong to the target ledger",
            )
    }

    if (currency != ledger.defaultCurrency) {
        errors +=
            ValidationError(
                field = "currency",
                code = ExpenseValidationCodes.CURRENCY_MISMATCH,
                message = "currency must match the ledger's defaultCurrency",
            )
    }

    if (errors.isNotEmpty()) return ValidationResult.Invalid(errors)

    val expenseId = newId()
    val memberCount = members.size
    val base = amount / memberCount
    val remainder = (amount % memberCount).value.toInt()
    val extraShareRecipients =
        members
            .sortedBy { it.id }
            .take(remainder)
            .map { it.id }
            .toSet()

    val splits =
        members.map { member ->
            val splitAmount = if (member.id in extraShareRecipients) base + MinorUnits(1L) else base
            Split(id = newId(), expenseId = expenseId, membershipId = member.id, amount = splitAmount)
        }

    val expense =
        Expense(
            id = expenseId,
            ledgerId = ledger.id,
            payerMembershipId = payerMembershipId,
            amount = amount,
            currency = currency,
            category = category,
            note = note,
            date = date,
            createdAt = createdAt,
            splitType = SplitType.EQUAL,
        )

    return ValidationResult.Valid(ExpenseWithSplits(expense, splits))
}
