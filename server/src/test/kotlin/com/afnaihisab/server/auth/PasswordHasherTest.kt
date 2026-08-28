package com.afnaihisab.server.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** ADR-0030 — Argon2id hashing/verification, and the timing-normalization helper AC-L2 relies on. */
class PasswordHasherTest {
    private val hasher = PasswordHasher()

    @Test
    fun `a hashed password verifies against the same raw password`() {
        val hash = hasher.hash("correct-horse-battery-staple")

        assertTrue(hasher.verify("correct-horse-battery-staple", hash))
    }

    @Test
    fun `a hashed password does not verify against a different password`() {
        val hash = hasher.hash("correct-horse-battery-staple")

        assertFalse(hasher.verify("wrong-password", hash))
    }

    @Test
    fun `hashing the same password twice produces different encoded hashes`() {
        // Argon2id salts each hash independently — this is what makes a rainbow-table attack
        // useless even against two users who happen to share a password.
        val first = hasher.hash("same-password")
        val second = hasher.hash("same-password")

        assertNotEquals(first, second)
    }

    @Test
    fun `verifyAgainstDummy never throws regardless of the password checked`() {
        hasher.verifyAgainstDummy("anything at all")
    }
}
