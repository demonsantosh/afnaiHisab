package com.afnaihisab.core.domain

import com.afnaihisab.core.validation.ValidationError
import com.afnaihisab.core.validation.ValidationResult

/**
 * Stable, machine-readable [com.afnaihisab.core.validation.ValidationError.code] values produced by
 * [validateRegistration] (`docs/specs/registration-login.md` AC-R3, AC-R4).
 */
object UserRegistrationValidationCodes {
    /** AC-R4: `email` is not a valid email shape. */
    const val INVALID_EMAIL_FORMAT: String = "invalid_email_format"

    /** AC-R3: `password` is under 8 characters (ADR-0030 — length only, no composition rules). */
    const val PASSWORD_TOO_SHORT: String = "password_too_short"
}

/** ADR-0030: minimum password length, no composition rules (NIST 800-63B). */
const val MIN_PASSWORD_LENGTH: Int = 8

/**
 * A deliberately permissive "does this look like an email" check, not full RFC 5322 validation —
 * current guidance (and this project's own low-stakes personal-app posture) treats an
 * over-strict email regex as a worse failure mode (rejecting valid addresses) than an under-strict
 * one (accepting a shape that later bounces on send, which Phase 2's email-verification step would
 * catch anyway, ADR-0030).
 */
private val EMAIL_SHAPE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/**
 * Validates the two account-lifecycle rules ADR-0030 decided for registration
 * (`docs/specs/registration-login.md` AC-R3, AC-R4). This is shape validation only — it never
 * touches hashing (server-only, ADR-0030: "`core` never sees a raw password or the hash" is about
 * the *hash*; validating the raw password's length here is the pure, shareable half of the rule)
 * and never checks email uniqueness (an I/O-bound, server-only concern, ADR-0001).
 *
 * Behavior:
 * - AC-R4: [email] not matching a basic email shape -> an error, `field = "email"`,
 *   `code = `[UserRegistrationValidationCodes.INVALID_EMAIL_FORMAT].
 * - AC-R3: [password] shorter than [MIN_PASSWORD_LENGTH] -> an error, `field = "password"`,
 *   `code = `[UserRegistrationValidationCodes.PASSWORD_TOO_SHORT].
 * - Both violations are reported together when both apply (`ValidationResult.Invalid.errors` is a
 *   list) — validation runs to completion before any rejection is returned, matching
 *   [createEqualSplitExpense]'s existing accumulate-all-violations pattern.
 */
fun validateRegistration(
    email: String,
    password: String,
): ValidationResult<Unit> {
    val errors = mutableListOf<ValidationError>()

    if (!EMAIL_SHAPE.matches(email)) {
        errors +=
            ValidationError(
                field = "email",
                code = UserRegistrationValidationCodes.INVALID_EMAIL_FORMAT,
                message = "email must be a valid email address",
            )
    }

    if (password.length < MIN_PASSWORD_LENGTH) {
        errors +=
            ValidationError(
                field = "password",
                code = UserRegistrationValidationCodes.PASSWORD_TOO_SHORT,
                message = "password must be at least $MIN_PASSWORD_LENGTH characters",
            )
    }

    if (errors.isNotEmpty()) return ValidationResult.Invalid(errors)

    return ValidationResult.Valid(Unit)
}
