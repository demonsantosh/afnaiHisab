package com.afnaihisab.core.domain

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
 * **Not yet implemented — TDD red phase (ADR-0009).** `TODO()` deliberately throws so every test
 * in `ExpenseSplittingTest` fails at runtime (not at compile time) until a later pass implements
 * the real allocation + validation logic. This is a human-review lane (ADR-0017) — the rounding
 * rule below must be implemented exactly as specified, not approximated.
 *
 * Expected behavior for the next pass:
 * - AC-1: exactly one [Split] per entry in [members], `splits.sumOf { it.amount } == amount`.
 * - AC-2 (largest-remainder method, `docs/FEATURES.md` §a): let `n = members.size`,
 *   `base = amount / n`, `remainder = (amount % n)`. Every member's split starts at `base`; the
 *   `remainder` members with the *largest fractional remainder* each get one extra minor unit.
 *   Because every member has an identical equal share, every fractional remainder is identical —
 *   so the tie always applies, and is broken by **ascending `membershipId`**: sort [members] by
 *   `id` ascending and give the first `remainder` of them `base + 1`, the rest `base`.
 * - AC-3: `amount <= 0` -> `Invalid` with one error, `field = "amount"`,
 *   `code = `[ExpenseValidationCodes.AMOUNT_NOT_POSITIVE]`, and no [Expense]/[Split] is
 *   constructed.
 * - AC-4: `payerMembershipId` not present in [members] (matched by [Membership.id], and that
 *   member's [Membership.ledgerId] must equal `ledger.id`) -> `Invalid`,
 *   `field = "payerMembershipId"`, `code = `[ExpenseValidationCodes.PAYER_NOT_MEMBER]`.
 * - AC-5: `currency != ledger.defaultCurrency` -> `Invalid`, `field = "currency"`,
 *   `code = `[ExpenseValidationCodes.CURRENCY_MISMATCH]`. Phase 1 has no conversion.
 * - Multiple violations may be reported together (`ValidationResult.Invalid.errors` is a list).
 *
 * @param members the ledger's *current* members — determines `n` for AC-2 and is what AC-4
 *   validates [payerMembershipId] against.
 * @param newId supplies each generated id: the [Expense]'s id first, then one per [Split] in the
 *   same order as [members] — injected (rather than calling `Uuid.random()` internally) so tests
 *   can assert on exact ids with deterministic input, per this project's ban on non-deterministic
 *   tests (`docs/adr/0009-testing-strategy.md`).
 */
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
): ValidationResult<ExpenseWithSplits> =
    TODO(
        "AC-1..AC-5 (docs/specs/expense-split-balance.md): equal-split expense creation with " +
            "largest-remainder rounding — implemented by a later pass, not this red-phase stub.",
    )
