package com.afnaihisab.core.validation

/**
 * A single rejected rule. [field] is a client-facing path (`"splits[2].amount"`), [code] is stable
 * and machine-readable, [message] is human-readable.
 */
data class ValidationError(
    val field: String,
    val code: String,
    val message: String,
)

/**
 * Result of applying domain rules to a candidate value.
 *
 * Split/balance rules are rejected *here*, in `core`, not at a UI form layer — the web form, the
 * Ktor route and (Phase 3/4) the mobile clients all run the same validation
 * (`docs/ARCHITECTURE.md` — "Domain logic").
 */
sealed interface ValidationResult<out T> {
    data class Valid<T>(
        val value: T,
    ) : ValidationResult<T>

    data class Invalid(
        val errors: List<ValidationError>,
    ) : ValidationResult<Nothing> {
        init {
            require(errors.isNotEmpty()) { "Invalid must carry at least one error" }
        }
    }
}
