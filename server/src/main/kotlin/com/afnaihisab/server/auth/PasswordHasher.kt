package com.afnaihisab.server.auth

import de.mkammerer.argon2.Argon2Factory

/**
 * Argon2id password hashing (ADR-0030) — the current OWASP-recommended algorithm, ranked above
 * bcrypt/scrypt in the Password Storage Cheat Sheet. `core` never imports this class or sees a raw
 * password/hash (ADR-0030): hashing and verification live in `server` only.
 *
 * Parameters are OWASP's minimum profile: memory 19 MiB, 2 iterations, parallelism 1.
 */
class PasswordHasher {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    /** Encoded Argon2id hash (algorithm, version, params, salt and hash all self-contained). */
    fun hash(rawPassword: String): String {
        val chars = rawPassword.toCharArray()
        try {
            return argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }

    /** `true` iff [rawPassword] hashes to [encodedHash] under the parameters encoded in the hash. */
    fun verify(
        rawPassword: String,
        encodedHash: String,
    ): Boolean {
        val chars = rawPassword.toCharArray()
        try {
            return argon2.verify(encodedHash, chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }

    /**
     * Runs a real Argon2id verification against a fixed, precomputed hash and discards the result.
     *
     * Used on login's user-not-found / ghost-user paths (`docs/specs/registration-login.md`
     * AC-L2/AC-L3) so the response time for "no such user" stays close to the response time for
     * "wrong password" — a real, deliberately expensive hash check runs either way, rather than an
     * early return short-circuiting straight to the error. This does not make timing attacks
     * impossible (a query-planner or network jitter can still vary), just closes the obvious gap
     * of "one path never touches Argon2 at all."
     */
    fun verifyAgainstDummy(rawPassword: String) {
        verify(rawPassword, dummyHash)
    }

    companion object {
        const val ITERATIONS: Int = 2
        const val MEMORY_KIB: Int = 19 * 1024
        const val PARALLELISM: Int = 1
    }
}

/**
 * Computed once (Argon2id is deliberately slow, ~tens of ms) against a fixed password that is
 * never anyone's real credential, purely so [PasswordHasher.verifyAgainstDummy] has a valid,
 * correctly-encoded hash to check against.
 */
private val dummyHash: String by lazy { PasswordHasher().hash("dummy-password-never-a-real-credential") }
