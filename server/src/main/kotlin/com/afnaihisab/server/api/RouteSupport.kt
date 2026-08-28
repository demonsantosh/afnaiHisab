package com.afnaihisab.server.api

import com.afnaihisab.core.data.api.ApiError
import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.core.data.api.ApiErrorEnvelope
import com.afnaihisab.core.domain.Membership
import com.afnaihisab.core.validation.ValidationResult
import com.afnaihisab.server.auth.authenticatedUserId
import com.afnaihisab.server.repository.MembershipRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Client-facing header carrying ADR-0023's client-generated idempotency key. Every mutating
 * financial endpoint requires it (AC-S2/AC-S3, `docs/specs/expense-split-balance-api.md`).
 */
private const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

/**
 * Parses [name] out of the route's path parameters as a [Uuid], responding `400` (ADR-0015's
 * envelope) and returning `null` on anything malformed — callers `return@post`/`return@get` on a
 * `null` result. Centralizes the parse-or-reject shape check every route needs (AC-S6 — routes
 * validate input shape, nothing more).
 */
suspend fun ApplicationCall.pathUuidOrRespondBadRequest(name: String): Uuid? {
    val raw = parameters[name]
    val parsed = raw?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    if (parsed == null) {
        respondError(
            status = HttpStatusCode.BadRequest,
            code = ApiErrorCode.VALIDATION_FAILED,
            message = "'$name' must be a valid UUID.",
        )
    }
    return parsed
}

/**
 * AC-S3: a `POST` without a well-formed `Idempotency-Key` header is rejected with `400` before any
 * repository is touched — this check runs *after* the ADR-0024 membership check below, so a
 * non-member request is rejected for that reason first (AC-S1's "without ... touching any
 * repository" already covers the membership lookup itself, which is the authorization check, not
 * the guarded write).
 */
suspend fun ApplicationCall.requireIdempotencyKey(): Uuid? {
    val header = request.header(IDEMPOTENCY_KEY_HEADER)
    val parsed = header?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    if (parsed == null) {
        respondError(
            status = HttpStatusCode.BadRequest,
            code = ApiErrorCode.VALIDATION_FAILED,
            message = "'$IDEMPOTENCY_KEY_HEADER' header is required and must be a UUID.",
        )
    }
    return parsed
}

/**
 * ADR-0024's authorization check, in one reusable place rather than reimplemented per route: the
 * authenticated caller must have a [Membership] in [ledgerId], or the request is rejected `403`
 * (AC-S1) without performing the requested operation or touching any other repository. Returns
 * the [Membership] itself since several routes need it (e.g. the caller's own membership isn't
 * separately re-fetched).
 */
suspend fun ApplicationCall.requireLedgerMembership(
    membershipRepository: MembershipRepository,
    ledgerId: Uuid,
): Membership? {
    val userId = authenticatedUserId()
    val membership = membershipRepository.find(ledgerId, userId)
    if (membership == null) {
        respondError(
            status = HttpStatusCode.Forbidden,
            code = ApiErrorCode.FORBIDDEN,
            message = "You are not a member of this ledger.",
        )
    }
    return membership
}

/**
 * Maps a rejected `core` [ValidationResult.Invalid] to the exact JSON body an idempotent repository
 * function stores/returns (ADR-0023 caches rejections too, not just successes) — the API error
 * `code` is the *first* violated rule's stable [com.afnaihisab.core.validation.ValidationError.code]
 * (e.g. [com.afnaihisab.core.domain.ExpenseValidationCodes.AMOUNT_NOT_POSITIVE]), and `message`
 * joins every violation so a multi-error rejection is never silently truncated to one line.
 *
 * `core`'s per-field validation codes are reused directly as the wire-level error code (rather than
 * a generic [ApiErrorCode.VALIDATION_FAILED]) since `core`'s own KDoc already designs them as "the
 * eventual server-layer error-envelope mapping[...]share one source of truth."
 */
fun ValidationResult.Invalid.toErrorResponseBody(json: Json): String {
    val first = errors.first()
    val message = errors.joinToString(separator = "; ") { it.message }
    return json.encodeToString(
        ApiErrorEnvelope.serializer(),
        ApiErrorEnvelope(ApiError(code = first.code, message = message)),
    )
}
