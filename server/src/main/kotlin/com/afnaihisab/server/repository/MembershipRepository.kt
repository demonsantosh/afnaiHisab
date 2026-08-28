package com.afnaihisab.server.repository

import com.afnaihisab.core.domain.Ledger
import com.afnaihisab.core.domain.Membership
import com.afnaihisab.core.domain.MembershipRole
import com.afnaihisab.core.domain.addMember
import com.afnaihisab.server.db.tables.MembershipsTable
import com.afnaihisab.server.idempotency.HTTP_STATUS_CREATED
import com.afnaihisab.server.idempotency.IdempotentResponse
import com.afnaihisab.server.idempotency.idempotent
import com.afnaihisab.server.routes.dto.MembershipResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * `Membership` reads/writes (`docs/domain-model.md` — Membership). [find] is what ADR-0024's
 * authorization check (`com.afnaihisab.server.api.requireLedgerMembership`) resolves against.
 */
interface MembershipRepository {
    suspend fun find(
        ledgerId: Uuid,
        userId: Uuid,
    ): Membership?

    /** All current members of [ledgerId] — the `members` `core`'s split/balance functions need. */
    suspend fun listByLedger(ledgerId: Uuid): List<Membership>

    /**
     * AC-12 has no rejection path (`core`'s [addMember] KDoc) — every call either returns the
     * cached [IdempotentResponse] for a repeated [idempotencyKey] or a fresh `201`.
     *
     * @param requesterUserId the *authenticated caller* adding the member — not necessarily
     *   [newUserId] — the idempotency key is scoped by this id (kotlin-expert-review finding,
     *   2026-08-28), never by [newUserId], since [newUserId] doesn't control this request at all.
     */
    suspend fun addMemberIdempotent(
        idempotencyKey: Uuid,
        requesterUserId: Uuid,
        ledger: Ledger,
        newUserId: Uuid,
        now: Instant,
    ): IdempotentResponse
}

/** The only class that imports Exposed types for this repository (`docs/guidelines/exposed-koin.md`). */
class ExposedMembershipRepository(
    private val database: Database,
    private val json: Json,
) : MembershipRepository {
    override suspend fun find(
        ledgerId: Uuid,
        userId: Uuid,
    ): Membership? =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                MembershipsTable
                    .selectAll()
                    .where { (MembershipsTable.ledgerId eq ledgerId) and (MembershipsTable.userId eq userId) }
                    .singleOrNull()
                    ?.toMembership()
            }
        }

    override suspend fun listByLedger(ledgerId: Uuid): List<Membership> =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                MembershipsTable
                    .selectAll()
                    .where { MembershipsTable.ledgerId eq ledgerId }
                    .map { it.toMembership() }
            }
        }

    override suspend fun addMemberIdempotent(
        idempotencyKey: Uuid,
        requesterUserId: Uuid,
        ledger: Ledger,
        newUserId: Uuid,
        now: Instant,
    ): IdempotentResponse =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                idempotent(userId = requesterUserId, key = idempotencyKey, now = now) {
                    val membership = addMember(ledger = ledger, newUserId = newUserId, joinedAt = now)

                    MembershipsTable.insert {
                        it[id] = membership.id
                        it[ledgerId] = membership.ledgerId
                        it[userId] = membership.userId
                        it[role] = membership.role.name
                        it[joinedAt] = membership.joinedAt
                    }

                    val body =
                        json.encodeToString(
                            MembershipResponse.serializer(),
                            MembershipResponse(
                                id = membership.id.toString(),
                                ledgerId = membership.ledgerId.toString(),
                                userId = membership.userId.toString(),
                                role = membership.role.name,
                                joinedAt = membership.joinedAt.toString(),
                            ),
                        )
                    IdempotentResponse(status = HTTP_STATUS_CREATED, body = body)
                }
            }
        }
}

private fun ResultRow.toMembership(): Membership =
    Membership(
        id = this[MembershipsTable.id],
        ledgerId = this[MembershipsTable.ledgerId],
        userId = this[MembershipsTable.userId],
        role = MembershipRole.valueOf(this[MembershipsTable.role]),
        joinedAt = this[MembershipsTable.joinedAt],
    )
