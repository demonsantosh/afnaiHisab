package com.afnaihisab.server.plugins

import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.server.api.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages

/**
 * Turns every failure into ADR-0015's single JSON envelope
 * (`{ "error": { "code": "...", "message": "..." } }`) — including the ones no route handler sees,
 * like an unmatched path or a deserialization failure.
 *
 * Internal detail never reaches the client: the exception is logged server-side, and the response
 * carries a stable code plus a generic message.
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.BadRequest,
                code = ApiErrorCode.VALIDATION_FAILED,
                message = cause.message ?: "Request could not be understood.",
            )
        }

        exception<NotFoundException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.NotFound,
                code = ApiErrorCode.NOT_FOUND,
                message = cause.message ?: "Resource not found.",
            )
        }

        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled failure on ${call.request.local.uri}", cause)
            call.respondError(
                status = HttpStatusCode.InternalServerError,
                code = ApiErrorCode.INTERNAL_ERROR,
                message = "Something went wrong. Please try again.",
            )
        }

        // Fires only when no route matched, so it cannot double-respond over a handler that
        // already produced a body.
        unhandled { call ->
            call.respondError(
                status = HttpStatusCode.NotFound,
                code = ApiErrorCode.NOT_FOUND,
                message = "No endpoint matches ${call.request.local.method.value} ${call.request.local.uri}.",
            )
        }
    }
}
