package com.afnaihisab.server.routes

import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.core.domain.CurrencyCode
import com.afnaihisab.server.api.pathUuidOrRespondBadRequest
import com.afnaihisab.server.api.requireIdempotencyKey
import com.afnaihisab.server.api.requireLedgerMembership
import com.afnaihisab.server.api.respondError
import com.afnaihisab.server.auth.authenticatedUserId
import com.afnaihisab.server.idempotency.IdempotentResponse
import com.afnaihisab.server.repository.LedgerRepository
import com.afnaihisab.server.repository.MembershipRepository
import com.afnaihisab.server.repository.UserRepository
import com.afnaihisab.server.routes.dto.AddMemberRequest
import com.afnaihisab.server.routes.dto.CreateLedgerRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.ktor.ext.inject
import kotlin.time.Clock

private const val CURRENCY_CODE_LENGTH = 3

/**
 * `POST /ledgers` (core AC-11) and `POST /ledgers/{ledgerId}/members` (core AC-12). Both require
 * `Authorization`/`Idempotency-Key`; ledger creation needs no ADR-0024 membership check (the ledger
 * doesn't exist yet), member addition does.
 */
fun Route.ledgerRoutes() {
    val ledgerRepository by inject<LedgerRepository>()
    val membershipRepository by inject<MembershipRepository>()
    val userRepository by inject<UserRepository>()

    post("/ledgers") {
        val idempotencyKey = call.requireIdempotencyKey() ?: return@post
        val request = call.receive<CreateLedgerRequest>()

        if (request.name.isBlank() || request.defaultCurrency.length != CURRENCY_CODE_LENGTH) {
            call.respondError(
                status = HttpStatusCode.BadRequest,
                code = ApiErrorCode.VALIDATION_FAILED,
                message = "name must be non-blank and defaultCurrency must be a 3-letter ISO 4217 code.",
            )
            return@post
        }

        val outcome =
            ledgerRepository.createLedgerIdempotent(
                idempotencyKey = idempotencyKey,
                ownerUserId = call.authenticatedUserId(),
                name = request.name,
                defaultCurrency = CurrencyCode(request.defaultCurrency),
                now = Clock.System.now(),
            )
        call.respondOutcome(outcome)
    }

    post("/ledgers/{ledgerId}/members") {
        val ledgerId = call.pathUuidOrRespondBadRequest("ledgerId") ?: return@post
        call.requireLedgerMembership(membershipRepository, ledgerId) ?: return@post
        val idempotencyKey = call.requireIdempotencyKey() ?: return@post
        val request = call.receive<AddMemberRequest>()

        val ledger = ledgerRepository.findById(ledgerId)
        val newUser = userRepository.findByEmail(request.email)
        if (ledger == null || newUser == null) {
            call.respondError(
                status = HttpStatusCode.BadRequest,
                code = ApiErrorCode.VALIDATION_FAILED,
                message = "No user is registered with that email.",
            )
            return@post
        }

        val outcome =
            membershipRepository.addMemberIdempotent(
                idempotencyKey = idempotencyKey,
                requesterUserId = call.authenticatedUserId(),
                ledger = ledger,
                newUserId = newUser.id,
                now = Clock.System.now(),
            )
        call.respondOutcome(outcome)
    }
}

/** Every idempotency-guarded write responds with exactly the JSON text the repository built/cached. */
internal suspend fun ApplicationCall.respondOutcome(outcome: IdempotentResponse) {
    respondText(outcome.body, ContentType.Application.Json, HttpStatusCode.fromValue(outcome.status))
}
