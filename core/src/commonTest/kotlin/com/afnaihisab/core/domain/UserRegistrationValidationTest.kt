package com.afnaihisab.core.domain

import com.afnaihisab.core.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `docs/specs/registration-login.md` AC-R3, AC-R4 — the pure, shareable half of registration
 * validation (email shape, password length). Uniqueness (AC-R2) and hashing (ADR-0030) are
 * `server`-only and covered by `server`'s own tests, not here (ADR-0001).
 */
class UserRegistrationValidationTest {
    @Test
    fun `AC-R1 a valid email and an 8+ character password validate clean`() {
        val result = validateRegistration(email = "person@example.com", password = "correct-horse")

        assertIs<ValidationResult.Valid<Unit>>(result)
    }

    @Test
    fun `AC-R3 a password under 8 characters is rejected with field password`() {
        val result = validateRegistration(email = "person@example.com", password = "short1")

        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals(1, invalid.errors.size)
        assertEquals("password", invalid.errors.single().field)
        assertEquals(UserRegistrationValidationCodes.PASSWORD_TOO_SHORT, invalid.errors.single().code)
    }

    @Test
    fun `AC-R3 exactly 8 characters is the minimum accepted length`() {
        val result = validateRegistration(email = "person@example.com", password = "12345678")

        assertIs<ValidationResult.Valid<Unit>>(result)
    }

    @Test
    fun `AC-R4 an email with no at sign is rejected with field email`() {
        val result = validateRegistration(email = "not-an-email", password = "correct-horse")

        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals(1, invalid.errors.size)
        assertEquals("email", invalid.errors.single().field)
        assertEquals(UserRegistrationValidationCodes.INVALID_EMAIL_FORMAT, invalid.errors.single().code)
    }

    @Test
    fun `AC-R4 an email with no domain dot is rejected with field email`() {
        val result = validateRegistration(email = "person@example", password = "correct-horse")

        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals("email", invalid.errors.single().field)
    }

    @Test
    fun `both an invalid email and a too-short password are reported together`() {
        val result = validateRegistration(email = "not-an-email", password = "short")

        val invalid = assertIs<ValidationResult.Invalid>(result)
        assertEquals(setOf("email", "password"), invalid.errors.map { it.field }.toSet())
    }
}
