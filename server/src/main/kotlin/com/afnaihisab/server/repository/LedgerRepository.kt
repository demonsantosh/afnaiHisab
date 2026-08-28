package com.afnaihisab.server.repository

import com.afnaihisab.core.domain.CurrencyCode
import com.afnaihisab.core.domain.Ledger
import com.afnaihisab.core.domain.createLedger
import com.afnaihisab.server.db.tables.LedgersTable
import com.afnaihisab.server.db.tables.MembershipsTable
import com.afnaihisab.server.idempotency.HTTP_STATUS_CREATED
import com.afnaihisab.server.idempotency.IdempotentResponse
import com.afnaihisab.server.idempotency.idempotent
import com.afnaihisab.server.routes.dto.LedgerResponse
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

/** Persists `core`'s `createLedger` result (`docs/specs/expense-split-balance.md` AC-11). */
interface LedgerRepository {
    suspend fun findById(id: Uuid): Ledger?

    /**
     * AC-11 has no rejection path (`core`'s [createLedger] KDoc) — every call either returns the
     * cached [IdempotentResponse] for a repeated [idempotencyKey] or a fresh `201`.
     *
     * The idempotency key is scoped by [ownerUserId] (kotlin-expert-review finding, 2026-08-28) —
     * no separate "requester id" parameter is needed here since the ledger's creator *is* the
     * authenticated requester for this endpoint.
     */
    suspend fun createLedgerIdempotent(
        idempotencyKey: Uuid,
        ownerUserId: Uuid,
        name: String,
        defaultCurrency: CurrencyCode,
        now: Instant,
    ): IdempotentResponse
}

/** The only class that imports Exposed types for this repository (`docs/guidelines/exposed-koin.md`). */
class ExposedLedgerRepository(
    private val database: Database,
    private val json: Json,
) : LedgerRepository {
    override suspend fun findById(id: Uuid): Ledger? =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                LedgersTable
                    .selectAll()
                    .where { LedgersTable.id eq id }
                    .singleOrNull()
                    ?.toLedger()
            }
        }

    override suspend fun createLedgerIdempotent(
        idempotencyKey: Uuid,
        ownerUserId: Uuid,
        name: String,
        defaultCurrency: CurrencyCode,
        now: Instant,
    ): IdempotentResponse =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                idempotent(userId = ownerUserId, key = idempotencyKey, now = now) {
                    val created = createLedger(name, defaultCurrency, ownerUserId, now)

                    LedgersTable.insert {
                        it[id] = created.ledger.id
                        it[LedgersTable.name] = created.ledger.name
                        it[LedgersTable.defaultCurrency] = created.ledger.defaultCurrency.value
                        it[createdAt] = created.ledger.createdAt
                    }
                    MembershipsTable.insert {
                        it[id] = created.ownerMembership.id
                        it[ledgerId] = created.ownerMembership.ledgerId
                        it[userId] = created.ownerMembership.userId
                        it[role] = created.ownerMembership.role.name
                        it[joinedAt] = created.ownerMembership.joinedAt
                    }

                    val body =
                        json.encodeToString(
                            LedgerResponse.serializer(),
                            LedgerResponse(
                                id = created.ledger.id.toString(),
                                name = created.ledger.name,
                                defaultCurrency = created.ledger.defaultCurrency.value,
                                createdAt = created.ledger.createdAt.toString(),
                                ownerMembershipId = created.ownerMembership.id.toString(),
                            ),
                        )
                    IdempotentResponse(status = HTTP_STATUS_CREATED, body = body)
                }
            }
        }
}

private fun ResultRow.toLedger(): Ledger =
    Ledger(
        id = this[LedgersTable.id],
        name = this[LedgersTable.name],
        defaultCurrency = CurrencyCode(this[LedgersTable.defaultCurrency]),
        createdAt = this[LedgersTable.createdAt],
        archivedAt = this[LedgersTable.archivedAt],
    )
