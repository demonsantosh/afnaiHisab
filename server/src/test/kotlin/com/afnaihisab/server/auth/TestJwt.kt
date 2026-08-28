package com.afnaihisab.server.auth

import com.afnaihisab.server.TEST_JWT_AUDIENCE
import com.afnaihisab.server.TEST_JWT_ISSUER
import com.afnaihisab.server.TEST_JWT_SECRET
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

/** Token lifetime for test-minted access tokens — long enough that no test run could ever expire mid-test. */
private val TEST_TOKEN_LIFETIME_MILLIS = TimeUnit.HOURS.toMillis(1)

/**
 * Mints a JWT for [userId] the same way a real login endpoint eventually will (ADR-0008) — signed
 * with [TEST_JWT_SECRET], the same secret [com.afnaihisab.server.testAppConfig] wires up, so
 * [configureAuthentication]'s verifier accepts it.
 *
 * **Registration/login are explicitly out of scope for
 * `docs/specs/expense-split-balance-api.md`** (see that file's "Out of scope" section) — this is
 * the documented, deliberate stand-in every test in this module uses instead of a real login call.
 * A real login endpoint is separate, still-needed Phase 1 work.
 *
 * @param issuedAtEpochMillis fixed rather than wall-clock (ADR-0009 — deterministic tests), but
 *   still real enough to always be within the token's validity window at assertion time.
 */
fun testJwt(
    userId: Uuid,
    issuedAtEpochMillis: Long = System.currentTimeMillis(),
): String {
    val issuedAt = Date(issuedAtEpochMillis)
    val expiresAt = Date(issuedAtEpochMillis + TEST_TOKEN_LIFETIME_MILLIS)
    return JWT
        .create()
        .withSubject(userId.toString())
        .withIssuer(TEST_JWT_ISSUER)
        .withAudience(TEST_JWT_AUDIENCE)
        // Must match configureAuthentication's validate block (auth/Jwt.kt), which now rejects
        // any bearer token whose `type` claim isn't "ACCESS" — a real refresh token issued by
        // JwtService must never authenticate a protected route.
        .withClaim("type", "ACCESS")
        .withIssuedAt(issuedAt)
        .withExpiresAt(expiresAt)
        .sign(Algorithm.HMAC256(TEST_JWT_SECRET))
}
