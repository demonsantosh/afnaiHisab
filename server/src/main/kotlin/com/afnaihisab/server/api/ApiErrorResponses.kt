package com.afnaihisab.server.api

import com.afnaihisab.core.data.api.ApiError
import com.afnaihisab.core.data.api.ApiErrorEnvelope
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Every route under this prefix (ADR-0015 — versioned from Phase 0, not retrofitted). */
const val API_V1: String = "/api/v1"

/**
 * The one way this server emits an error. Routes and the StatusPages handlers both go through here
 * so no endpoint can invent its own error shape — the failure mode ADR-0015 exists to prevent.
 *
 * The envelope type itself lives in `core` (`ApiErrorEnvelope`), shared with every client.
 */
suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
) {
    respond(status, ApiErrorEnvelope(ApiError(code = code, message = message)))
}
