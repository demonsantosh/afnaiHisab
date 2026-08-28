package com.afnaihisab.server.routes

import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.core.domain.CurrencyCode
import com.afnaihisab.core.domain.MinorUnits
import com.afnaihisab.server.api.pathUuidOrRespondBadRequest
import com.afnaihisab.server.api.requireIdempotencyKey
import com.afnaihisab.server.api.requireLedgerMembership
import com.afnaihisab.server.api.respondError
import com.afnaihisab.server.auth.authenticatedUserId
import com.afnaihisab.server.repository.ExpenseRepository
import com.afnaihisab.server.repository.MembershipRepository
import com.afnaihisab.server.repository.SettlementRepository
import com.afnaihisab.server.routes.dto.CreateSettlementRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * `POST /ledgers/{ledgerId}/settlements` (core AC-8..AC-10, AC-13). `core`'s `recordSettlement`
 * deliberately does not validate that `fromMembershipId`/`toMembershipId` belong to the ledger
 * (see its KDoc) — that check is this route's job, not a reimplementation of AC-9/AC-10's math.
 */
fun Route.settlementRoutes() {
    val membershipRepository by inject<MembershipRepository>()
    val expenseRepository by inject<ExpenseRepository>()
    val settlementRepository by inject<SettlementRepository>()

    post("/ledgers/{ledgerId}/settlements") {
        val ledgerId = call.pathUuidOrRespondBadRequest("ledgerId") ?: return@post
        call.requireLedgerMembership(membershipRepository, ledgerId) ?: return@post
        val idempotencyKey = call.requireIdempotencyKey() ?: return@post
        val request = call.receive<CreateSettlementRequest>()

        val fromMembershipId = runCatching { Uuid.parse(request.fromMembershipId) }.getOrNull()
        val toMembershipId = runCatching { Uuid.parse(request.toMembershipId) }.getOrNull()
        if (fromMembershipId == null || toMembershipId == null) {
            call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCode.VALIDATION_FAILED,
                "fromMembershipId and toMembershipId must be valid UUIDs.",
            )
            return@post
        }

        val members = membershipRepository.listByLedger(ledgerId)
        val memberIds = members.map { it.id }.toSet()
        if (fromMembershipId !in memberIds || toMembershipId !in memberIds) {
            call.respondError(
                HttpStatusCode.BadRequest,
                ApiErrorCode.VALIDATION_FAILED,
                "fromMembershipId and toMembershipId must both belong to this ledger.",
            )
            return@post
        }

        val (expenses, splits) = expenseRepository.allForLedger(ledgerId)
        val existingSettlements = settlementRepository.listByLedger(ledgerId)

        val outcome =
            settlementRepository.recordSettlementIdempotent(
                idempotencyKey = idempotencyKey,
                requesterUserId = call.authenticatedUserId(),
                members = members,
                expenses = expenses,
                splits = splits,
                existingSettlements = existingSettlements,
                ledgerId = ledgerId,
                fromMembershipId = fromMembershipId,
                toMembershipId = toMembershipId,
                amount = MinorUnits(request.amount),
                currency = CurrencyCode(request.currency),
                note = request.note,
                now = Clock.System.now(),
            )
        call.respondOutcome(outcome)
    }
}
