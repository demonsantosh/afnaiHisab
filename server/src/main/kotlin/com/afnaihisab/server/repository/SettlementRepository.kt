package com.afnaihisab.server.repository

import com.afnaihisab.core.domain.CurrencyCode
import com.afnaihisab.core.domain.Expense
import com.afnaihisab.core.domain.Membership
import com.afnaihisab.core.domain.MinorUnits
import com.afnaihisab.core.domain.Settlement
import com.afnaihisab.core.domain.SettlementRecord
import com.afnaihisab.core.domain.Split
import com.afnaihisab.core.domain.recordSettlement
import com.afnaihisab.core.validation.ValidationResult
import com.afnaihisab.server.api.toErrorResponseBody
import com.afnaihisab.server.db.tables.SettlementsTable
import com.afnaihisab.server.idempotency.HTTP_STATUS_CREATED
import com.afnaihisab.server.idempotency.HTTP_STATUS_VALIDATION_REJECTED
import com.afnaihisab.server.idempotency.IdempotentResponse
import com.afnaihisab.server.idempotency.idempotent
import com.afnaihisab.server.routes.dto.SettlementResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * `Settlement` reads/writes (`docs/domain-model.md` — Settlement). [recordSettlementIdempotent]
 * wraps `core`'s [recordSettlement] (AC-8..AC-10, AC-13).
 */
interface SettlementRepository {
    suspend fun listByLedger(ledgerId: Uuid): List<Settlement>

    /**
     * The caller (the settlement route) already fetched [members]/[expenses]/[splits]/
     * [existingSettlements] via their own repositories — each of those reads is its own separate,
     * already-committed transaction, which is fine for a read; this function's *own* single
     * transaction only ever does the idempotency check plus (on a fresh key) the one `settlements`
     * row insert, matching `docs/guidelines/exposed-koin.md`'s "one transaction per repository
     * function." An `Invalid` core result is stored/returned as `400` just like a `Valid` one is
     * stored/returned as `201` (ADR-0023 caches rejections too).
     *
     * @param requesterUserId the *authenticated caller* — the idempotency key is scoped by this id
     *   (kotlin-expert-review finding, 2026-08-28), not by [fromMembershipId]/[toMembershipId] or
     *   any other request-supplied value.
     */
    @Suppress("LongParameterList")
    suspend fun recordSettlementIdempotent(
        idempotencyKey: Uuid,
        requesterUserId: Uuid,
        members: List<Membership>,
        expenses: List<Expense>,
        splits: List<Split>,
        existingSettlements: List<Settlement>,
        ledgerId: Uuid,
        fromMembershipId: Uuid,
        toMembershipId: Uuid,
        amount: MinorUnits,
        currency: CurrencyCode,
        note: String?,
        now: Instant,
    ): IdempotentResponse
}

/** The only class that imports Exposed types for this repository (`docs/guidelines/exposed-koin.md`). */
class ExposedSettlementRepository(
    private val database: Database,
    private val json: Json,
) : SettlementRepository {
    override suspend fun listByLedger(ledgerId: Uuid): List<Settlement> =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                SettlementsTable
                    .selectAll()
                    .where { SettlementsTable.ledgerId eq ledgerId }
                    .map { it.toSettlement() }
            }
        }

    @Suppress("LongParameterList")
    override suspend fun recordSettlementIdempotent(
        idempotencyKey: Uuid,
        requesterUserId: Uuid,
        members: List<Membership>,
        expenses: List<Expense>,
        splits: List<Split>,
        existingSettlements: List<Settlement>,
        ledgerId: Uuid,
        fromMembershipId: Uuid,
        toMembershipId: Uuid,
        amount: MinorUnits,
        currency: CurrencyCode,
        note: String?,
        now: Instant,
    ): IdempotentResponse =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                idempotent(userId = requesterUserId, key = idempotencyKey, now = now) {
                    when (
                        val result =
                            recordSettlement(
                                members = members,
                                expenses = expenses,
                                splits = splits,
                                existingSettlements = existingSettlements,
                                ledgerId = ledgerId,
                                fromMembershipId = fromMembershipId,
                                toMembershipId = toMembershipId,
                                amount = amount,
                                currency = currency,
                                note = note,
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

    private fun persistAndRespond(record: SettlementRecord): IdempotentResponse {
        val settlement = record.settlement
        SettlementsTable.insert {
            it[id] = settlement.id
            it[ledgerId] = settlement.ledgerId
            it[fromMembershipId] = settlement.fromMembershipId
            it[toMembershipId] = settlement.toMembershipId
            it[amount] = settlement.amount.value
            it[currency] = settlement.currency.value
            it[note] = settlement.note
            it[createdAt] = settlement.createdAt
        }

        val body =
            json.encodeToString(
                SettlementResponse.serializer(),
                SettlementResponse(
                    id = settlement.id.toString(),
                    ledgerId = settlement.ledgerId.toString(),
                    fromMembershipId = settlement.fromMembershipId.toString(),
                    toMembershipId = settlement.toMembershipId.toString(),
                    amount = settlement.amount.value,
                    currency = settlement.currency.value,
                    note = settlement.note,
                    createdAt = settlement.createdAt.toString(),
                    fromBalanceBefore = record.fromBefore.netBalance.value,
                    toBalanceBefore = record.toBefore.netBalance.value,
                    fromBalanceAfter = record.fromAfter.netBalance.value,
                    toBalanceAfter = record.toAfter.netBalance.value,
                ),
            )
        return IdempotentResponse(status = HTTP_STATUS_CREATED, body = body)
    }
}

private fun ResultRow.toSettlement(): Settlement =
    Settlement(
        id = this[SettlementsTable.id],
        ledgerId = this[SettlementsTable.ledgerId],
        fromMembershipId = this[SettlementsTable.fromMembershipId],
        toMembershipId = this[SettlementsTable.toMembershipId],
        amount = MinorUnits(this[SettlementsTable.amount]),
        currency = CurrencyCode(this[SettlementsTable.currency]),
        note = this[SettlementsTable.note],
        createdAt = this[SettlementsTable.createdAt],
    )
