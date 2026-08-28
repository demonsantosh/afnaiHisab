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
 * @property field which request field this error is about (`"password"`, `"email"`), for a
 *   field-level validation rejection (`docs/specs/registration-login.md` AC-R3/AC-R4). `null` for
 *   an error that isn't about one specific field. A single [ApiError] carries at most one field —
 *   when [com.afnaihisab.core.validation.ValidationResult.Invalid] accumulates more than one
 *   violation, the server maps only the first to the wire envelope; this is a deliberate,
 *   documented simplification of ADR-0015's one-code/one-message/one-field envelope shape, not a
 *   bug — `core`'s [com.afnaihisab.core.validation.ValidationResult] itself still carries every
 *   violation for any future consumer that wants all of them at once.
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val field: String? = null,
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
