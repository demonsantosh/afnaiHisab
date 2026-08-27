package com.afnaihisab.core.data.api

import kotlinx.serialization.Serializable

/**
 * The single JSON error envelope every endpoint returns (ADR-0015):
 * `{ "error": { "code": "...", "message": "..." } }`.
 *
 * It lives in `core` rather than `server` on purpose — web today, Android/iOS later, all parse
 * errors through this one shape.
 */
@Serializable
data class ApiErrorEnvelope(
    val error: ApiError,
)

/**
 * @property code a stable, machine-readable identifier clients may branch on — see [ApiErrorCode].
 * @property message human-readable text, safe to show a user; never leaks internals or stack traces.
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

/**
 * Error codes shared by server and clients. Add codes here as endpoints need them so that no
 * endpoint invents a one-off spelling (the failure mode ADR-0015 exists to prevent).
 */
object ApiErrorCode {
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val NOT_FOUND = "NOT_FOUND"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val CONFLICT = "CONFLICT"
}
