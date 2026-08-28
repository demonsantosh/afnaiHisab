package com.afnaihisab.server.repository

import com.afnaihisab.core.domain.CurrencyCode
import com.afnaihisab.core.domain.Expense
import com.afnaihisab.core.domain.ExpenseWithSplits
import com.afnaihisab.core.domain.Ledger
import com.afnaihisab.core.domain.Membership
import com.afnaihisab.core.domain.MinorUnits
import com.afnaihisab.core.domain.Split
import com.afnaihisab.core.domain.SplitType
import com.afnaihisab.core.domain.createEqualSplitExpense
import com.afnaihisab.core.validation.ValidationResult
import com.afnaihisab.server.api.toErrorResponseBody
import com.afnaihisab.server.db.tables.ExpensesTable
import com.afnaihisab.server.db.tables.SplitsTable
import com.afnaihisab.server.idempotency.HTTP_STATUS_CREATED
import com.afnaihisab.server.idempotency.HTTP_STATUS_VALIDATION_REJECTED
import com.afnaihisab.server.idempotency.IdempotentResponse
import com.afnaihisab.server.idempotency.idempotent
import com.afnaihisab.server.routes.dto.ExpenseResponse
import com.afnaihisab.server.routes.dto.SplitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** One page of an `expenses`-history query (ADR-0015 cursor pagination, AC-S5's clamp rule). */
data class ExpensePage(
    val items: List<ExpenseWithSplits>,
    val nextCursor: Uuid?,
)

/**
 * `Expense`/`Split` reads/writes (`docs/domain-model.md` — Expense, Split). [createExpenseIdempotent]
 * is the sole entry point for AC-1..AC-5's equal-split creation.
 */
interface ExpenseRepository {
    /** Every `Expense` + its `Split`s for [ledgerId] — the `expenses`/`splits` `calculateBalances` needs. */
    suspend fun allForLedger(ledgerId: Uuid): Pair<List<Expense>, List<Split>>

    /**
     * Newest-first (descending id — UUIDv7 is time-sortable, `docs/domain-model.md` "ID strategy").
     * [limit] is expected already clamped to `1..200` by the route (AC-S5); [cursor], when
     * present, is the previous page's last item's id.
     */
    suspend fun listByLedger(
        ledgerId: Uuid,
        cursor: Uuid?,
        limit: Int,
    ): ExpensePage

    /**
     * Runs [com.afnaihisab.core.domain.createEqualSplitExpense] (AC-1..AC-5) inside the
     * idempotency-guarded transaction: an `Invalid` result is stored/returned as `400` just like a
     * `Valid` one is stored/returned as `201` (ADR-0023 caches rejections too).
     *
     * @param requesterUserId the *authenticated caller* — the idempotency key is scoped by this id
     *   (kotlin-expert-review finding, 2026-08-28), not by [payerMembershipId] or any other
     *   request-supplied value.
     */
    @Suppress("LongParameterList")
    suspend fun createExpenseIdempotent(
        idempotencyKey: Uuid,
        requesterUserId: Uuid,
        ledger: Ledger,
        members: List<Membership>,
        payerMembershipId: Uuid,
        amount: MinorUnits,
        currency: CurrencyCode,
        category: String,
        note: String?,
        date: LocalDate,
        now: Instant,
    ): IdempotentResponse
}

/** The only class that imports Exposed types for this repository (`docs/guidelines/exposed-koin.md`). */
class ExposedExpenseRepository(
    private val database: Database,
    private val json: Json,
) : ExpenseRepository {
    override suspend fun allForLedger(ledgerId: Uuid): Pair<List<Expense>, List<Split>> =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                val expenses =
                    ExpensesTable
                        .selectAll()
                        .where { ExpensesTable.ledgerId eq ledgerId }
                        .map { it.toExpense() }
                val splits = splitsFor(expenses.map { it.id })
                expenses to splits
            }
        }

    override suspend fun listByLedger(
        ledgerId: Uuid,
        cursor: Uuid?,
        limit: Int,
    ): ExpensePage =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                val query = ExpensesTable.selectAll().where { ledgerCondition(ledgerId, cursor) }
                val rows =
                    query
                        .orderBy(ExpensesTable.id, SortOrder.DESC)
                        // Fetch one extra row to know whether there's a next page without a second query.
                        .limit(limit + 1)
                        .toList()

                val hasMore = rows.size > limit
                val pageRows = rows.take(limit)
                val expenses = pageRows.map { it.toExpense() }
                val splitsByExpense = splitsFor(expenses.map { it.id }).groupBy { it.expenseId }

                ExpensePage(
                    items = expenses.map { expense -> ExpenseWithSplits(expense, splitsByExpense[expense.id].orEmpty()) },
                    nextCursor = if (hasMore) expenses.lastOrNull()?.id else null,
                )
            }
        }

    @Suppress("LongParameterList")
    override suspend fun createExpenseIdempotent(
        idempotencyKey: Uuid,
        requesterUserId: Uuid,
        ledger: Ledger,
        members: List<Membership>,
        payerMembershipId: Uuid,
        amount: MinorUnits,
        currency: CurrencyCode,
        category: String,
        note: String?,
        date: LocalDate,
        now: Instant,
    ): IdempotentResponse =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                idempotent(userId = requesterUserId, key = idempotencyKey, now = now) {
                    when (
                        val result =
                            createEqualSplitExpense(
                                ledger = ledger,
                                members = members,
                                payerMembershipId = payerMembershipId,
                                amount = amount,
                                currency = currency,
                                category = category,
                                note = note,
                                date = date,
                                createdAt = now,
                            )
                    ) {
                        is ValidationResult.Invalid ->
                            IdempotentResponse(HTTP_STATUS_VALIDATION_REJECTED, result.toErrorResponseBody(json))

                        is ValidationResult.Valid -> persistAndRespond(result.value)
                    }
                }
            }
        }

    private fun persistAndRespond(created: ExpenseWithSplits): IdempotentResponse {
        val expense = created.expense
        ExpensesTable.insert {
            it[id] = expense.id
            it[ledgerId] = expense.ledgerId
            it[payerMembershipId] = expense.payerMembershipId
            it[amount] = expense.amount.value
            it[currency] = expense.currency.value
            it[category] = expense.category
            it[note] = expense.note
            it[expenseDate] = expense.date
            it[createdAt] = expense.createdAt
            it[splitType] = expense.splitType.name
            it[isLocked] = expense.isLocked
        }
        SplitsTable.batchInsert(created.splits) { split ->
            this[SplitsTable.id] = split.id
            this[SplitsTable.expenseId] = split.expenseId
            this[SplitsTable.membershipId] = split.membershipId
            this[SplitsTable.amount] = split.amount.value
            this[SplitsTable.shareValue] = split.shareValue
        }

        val body =
            json.encodeToString(
                ExpenseResponse.serializer(),
                ExpenseResponse(
                    id = expense.id.toString(),
                    ledgerId = expense.ledgerId.toString(),
                    payerMembershipId = expense.payerMembershipId.toString(),
                    amount = expense.amount.value,
                    currency = expense.currency.value,
                    category = expense.category,
                    note = expense.note,
                    date = expense.date.toString(),
                    createdAt = expense.createdAt.toString(),
                    splitType = expense.splitType.name,
                    splits =
                        created.splits.map {
                            SplitResponse(id = it.id.toString(), membershipId = it.membershipId.toString(), amount = it.amount.value)
                        },
                ),
            )
        return IdempotentResponse(status = HTTP_STATUS_CREATED, body = body)
    }

    private fun ledgerCondition(
        ledgerId: Uuid,
        cursor: Uuid?,
    ) = if (cursor == null) {
        ExpensesTable.ledgerId eq ledgerId
    } else {
        (ExpensesTable.ledgerId eq ledgerId) and (ExpensesTable.id less cursor)
    }

    private fun splitsFor(expenseIds: List<Uuid>): List<Split> =
        if (expenseIds.isEmpty()) {
            emptyList()
        } else {
            SplitsTable
                .selectAll()
                .where { SplitsTable.expenseId inList expenseIds }
                .map { it.toSplit() }
        }
}

private fun ResultRow.toExpense(): Expense =
    Expense(
        id = this[ExpensesTable.id],
        ledgerId = this[ExpensesTable.ledgerId],
        payerMembershipId = this[ExpensesTable.payerMembershipId],
        amount = MinorUnits(this[ExpensesTable.amount]),
        currency = CurrencyCode(this[ExpensesTable.currency]),
        category = this[ExpensesTable.category],
        note = this[ExpensesTable.note],
        date = this[ExpensesTable.expenseDate],
        createdAt = this[ExpensesTable.createdAt],
        splitType = SplitType.valueOf(this[ExpensesTable.splitType]),
        isLocked = this[ExpensesTable.isLocked],
    )

private fun ResultRow.toSplit(): Split =
    Split(
        id = this[SplitsTable.id],
        expenseId = this[SplitsTable.expenseId],
        membershipId = this[SplitsTable.membershipId],
        amount = MinorUnits(this[SplitsTable.amount]),
        shareValue = this[SplitsTable.shareValue],
    )
