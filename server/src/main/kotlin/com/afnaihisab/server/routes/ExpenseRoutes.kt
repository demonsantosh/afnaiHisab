package com.afnaihisab.server.routes

import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.core.data.api.CursorPage
import com.afnaihisab.core.domain.CurrencyCode
import com.afnaihisab.core.domain.ExpenseWithSplits
import com.afnaihisab.core.domain.MinorUnits
import com.afnaihisab.server.api.pathUuidOrRespondBadRequest
import com.afnaihisab.server.api.requireIdempotencyKey
import com.afnaihisab.server.api.requireLedgerMembership
import com.afnaihisab.server.api.respondError
import com.afnaihisab.server.auth.authenticatedUserId
import com.afnaihisab.server.repository.ExpenseRepository
import com.afnaihisab.server.repository.LedgerRepository
import com.afnaihisab.server.repository.MembershipRepository
import com.afnaihisab.server.routes.dto.CreateExpenseRequest
import com.afnaihisab.server.routes.dto.ExpenseResponse
import com.afnaihisab.server.routes.dto.SplitResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.datetime.LocalDate
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** ADR-0026's pagination limits: default page size and the hard cap a request is clamped to. */
private const val DEFAULT_PAGE_SIZE = 50
private const val MAX_PAGE_SIZE = 200

/**
 * `POST /ledgers/{ledgerId}/expenses` (core AC-1..AC-5) and `GET /ledgers/{ledgerId}/expenses`
 * (cursor-paginated history, ADR-0015/ADR-0026) — one route registration function each, so early
 * `return@post`/`return@get` guard clauses stay inside their own route handler.
 */
fun Route.expenseRoutes() {
    createExpenseRoute()
    listExpensesRoute()
}

private fun Route.createExpenseRoute() {
    val ledgerRepository by inject<LedgerRepository>()
    val membershipRepository by inject<MembershipRepository>()
    val expenseRepository by inject<ExpenseRepository>()

    post("/ledgers/{ledgerId}/expenses") {
        val ledgerId = call.pathUuidOrRespondBadRequest("ledgerId") ?: return@post
        call.requireLedgerMembership(membershipRepository, ledgerId) ?: return@post
        val idempotencyKey = call.requireIdempotencyKey() ?: return@post
        val request = call.receive<CreateExpenseRequest>()

        val ledger = ledgerRepository.findById(ledgerId)
        val payerMembershipId = runCatching { Uuid.parse(request.payerMembershipId) }.getOrNull()
        val date = runCatching { LocalDate.parse(request.date) }.getOrNull()
        if (ledger == null || payerMembershipId == null || date == null) {
            call.respondError(
                status = HttpStatusCode.BadRequest,
                code = ApiErrorCode.VALIDATION_FAILED,
                message = "ledgerId, payerMembershipId must be valid UUIDs and date a valid ISO-8601 date.",
            )
            return@post
        }

        val members = membershipRepository.listByLedger(ledgerId)
        val outcome =
            expenseRepository.createExpenseIdempotent(
                idempotencyKey = idempotencyKey,
                requesterUserId = call.authenticatedUserId(),
                ledger = ledger,
                members = members,
                payerMembershipId = payerMembershipId,
                amount = MinorUnits(request.amount),
                currency = CurrencyCode(request.currency),
                category = request.category,
                note = request.note,
                date = date,
                now = Clock.System.now(),
            )
        call.respondOutcome(outcome)
    }
}

private fun Route.listExpensesRoute() {
    val membershipRepository by inject<MembershipRepository>()
    val expenseRepository by inject<ExpenseRepository>()

    get("/ledgers/{ledgerId}/expenses") {
        val ledgerId = call.pathUuidOrRespondBadRequest("ledgerId") ?: return@get
        call.requireLedgerMembership(membershipRepository, ledgerId) ?: return@get

        val cursorParam = call.request.queryParameters["cursor"]
        val cursor = cursorParam?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        if (cursorParam != null && cursor == null) {
            call.respondError(HttpStatusCode.BadRequest, ApiErrorCode.VALIDATION_FAILED, "cursor must be a valid UUID.")
            return@get
        }
        val limit =
            call.request.queryParameters["limit"]
                ?.toIntOrNull()
                ?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE

        val page = expenseRepository.listByLedger(ledgerId, cursor, limit)
        call.respond(
            CursorPage(
                items = page.items.map { it.toResponse() },
                nextCursor = page.nextCursor?.toString(),
            ),
        )
    }
}

private fun ExpenseWithSplits.toResponse(): ExpenseResponse =
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
        splits = splits.map { SplitResponse(it.id.toString(), it.membershipId.toString(), it.amount.value) },
    )
