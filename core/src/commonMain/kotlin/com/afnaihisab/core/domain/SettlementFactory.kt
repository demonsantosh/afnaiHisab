package com.afnaihisab.core.domain

import com.afnaihisab.core.validation.ValidationResult
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Stable, machine-readable [com.afnaihisab.core.validation.ValidationError.code] values produced by
 * [createSettlement] (`docs/specs/expense-split-balance.md` AC-9, AC-10).
 */
object SettlementValidationCodes {
    /** AC-9: `fromMembershipId == toMembershipId`. */
    const val SAME_MEMBERSHIP: String = "same_membership"

    /** AC-10: `amount <= 0`. */
    const val AMOUNT_NOT_POSITIVE: String = "amount_not_positive"
}

/**
 * Validates and creates a [Settlement] between two distinct memberships of the same ledger
 * (`docs/specs/expense-split-balance.md` AC-8..AC-10).
 *
 * **Not yet implemented — TDD red phase (ADR-0009).** `TODO()` deliberately throws so every test
 * in `SettlementValidationTest` fails at runtime until a later pass implements the real
 * validation. This is a human-review lane (ADR-0017).
 *
 * Expected behavior for the next pass:
 * - AC-8: distinct `fromMembershipId`/`toMembershipId`, `amount > 0` -> `Valid(Settlement(...))`
 *   with all fields carried through verbatim. Callers then fold the result into
 *   [calculateBalances] (AC-6) — this function does not touch balances itself.
 * - AC-9: `fromMembershipId == toMembershipId` -> `Invalid`, `field = "toMembershipId"`,
 *   `code = `[SettlementValidationCodes.SAME_MEMBERSHIP]`, no [Settlement] constructed.
 * - AC-10: `amount <= 0` -> `Invalid`, `field = "amount"`,
 *   `code = `[SettlementValidationCodes.AMOUNT_NOT_POSITIVE]`.
 * - Both AC-9 and AC-10 violating at once may report both errors together.
 *
 * This function intentionally does **not** validate that `fromMembershipId`/`toMembershipId`
 * belong to `ledgerId`'s membership set — that check requires the caller's membership list and is
 * out of scope for the 12 ACs in `docs/specs/expense-split-balance.md`; a caller wiring this into
 * `server` is expected to check membership existence separately before calling this function.
 *
 * @param id the settlement's id — injected (not `Uuid.random()` internally) so tests stay
 *   deterministic (`docs/adr/0009-testing-strategy.md`).
 *
 * `@Suppress`: `UnusedParameter` because the body is a deliberate red-phase `TODO()` (ADR-0009).
 * `LongParameterList` because these are exactly the [Settlement] fields plus an injected `id` for
 * deterministic tests.
 */
@Suppress("UnusedParameter", "LongParameterList")
fun createSettlement(
    ledgerId: Uuid,
    fromMembershipId: Uuid,
    toMembershipId: Uuid,
    amount: MinorUnits,
    currency: CurrencyCode,
    note: String? = null,
    createdAt: Instant,
    id: Uuid = Uuid.random(),
): ValidationResult<Settlement> =
    TODO(
        "AC-8..AC-10 (docs/specs/expense-split-balance.md): settlement validation — " +
            "implemented by a later pass, not this red-phase stub.",
    )

/**
 * The result of [recordSettlement]: the [Settlement] itself, plus the two parties' balances
 * immediately before and after it — so what was settled, and whether anything remains, is never
 * ambiguous (`docs/specs/expense-split-balance.md` AC-13).
 */
data class SettlementRecord(
    val settlement: Settlement,
    val fromBefore: MemberBalance,
    val toBefore: MemberBalance,
    val fromAfter: MemberBalance,
    val toAfter: MemberBalance,
)

/**
 * Validates and creates a settlement (via [createSettlement]), then reports both parties'
 * [calculateBalances] result immediately before and immediately after it
 * (`docs/specs/expense-split-balance.md` AC-13).
 *
 * **Not yet implemented — TDD red phase (ADR-0009).** `TODO()` deliberately throws so every test
 * in `SettlementRecordTest` fails at runtime until a later pass implements the real composition.
 * This is a human-review lane (ADR-0017).
 *
 * Expected behavior for the next pass:
 * 1. Call [createSettlement] with the settlement fields. If it returns `Invalid`, return that same
 *    `Invalid` unchanged (it is already a `ValidationResult<Nothing>`, so no reconstruction is
 *    needed) — [existingSettlements]/[calculateBalances] are never touched on the rejected path.
 * 2. If `Valid`, compute `before = calculateBalances(members, expenses, splits, existingSettlements)`
 *    and `after = calculateBalances(members, expenses, splits, existingSettlements + settlement)`.
 * 3. Look up [fromMembershipId]/[toMembershipId] in both `before` and `after` and return
 *    `Valid(SettlementRecord(settlement, fromBefore, toBefore, fromAfter, toAfter))`.
 *
 * This function deliberately does **not** add a stored field to [Settlement] or link it to
 * specific expenses — that would be a bigger, Splitwise-diverging design change AC-13 explicitly
 * does not make. Clarity comes from always reporting balance context, not from record-keeping.
 *
 * @param members,expenses,splits,existingSettlements the ledger's full current state, as
 *   [calculateBalances] already expects it — [existingSettlements] excludes the settlement being
 *   recorded by this call.
 *
 * `@Suppress`: `UnusedParameter` because the body is a deliberate red-phase `TODO()` (ADR-0009).
 * `LongParameterList` because AC-13 composes [createSettlement] over [calculateBalances], so this
 * signature is deliberately the union of both — the spec explicitly rejects storing the ledger
 * state on [Settlement] instead.
 */
@Suppress("UnusedParameter", "LongParameterList")
fun recordSettlement(
    members: List<Membership>,
    expenses: List<Expense>,
    splits: List<Split>,
    existingSettlements: List<Settlement>,
    ledgerId: Uuid,
    fromMembershipId: Uuid,
    toMembershipId: Uuid,
    amount: MinorUnits,
    currency: CurrencyCode,
    note: String? = null,
    createdAt: Instant,
    id: Uuid = Uuid.random(),
): ValidationResult<SettlementRecord> =
    TODO(
        "AC-13 (docs/specs/expense-split-balance.md): settlement + before/after balance " +
            "composition — implemented by a later pass, not this red-phase stub.",
    )
