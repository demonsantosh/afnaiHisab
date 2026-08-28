package com.afnaihisab.server.auth

import com.afnaihisab.server.config.JwtConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ADR-0008 — JWT access/refresh token issuance and verification.
 *
 * [issuedAt] deliberately reads the real clock rather than a fixed instant: the java-jwt verifier
 * checks `exp`/`iat` against actual wall-clock time (it has no injectable clock in this service),
 * so a hardcoded past timestamp would make even a freshly issued token look expired. This is a
 * `server`-infrastructure test, not `core` domain logic, so ADR-0009's no-wall-clock-reads
 * discipline doesn't apply here.
 */
@OptIn(ExperimentalUuidApi::class)
class JwtServiceTest {
    private val config =
        JwtConfig(
            secret = "test-signing-secret",
            issuer = "afnaihisab-test",
            audience = "afnaihisab-test-clients",
            realm = "afnaihisab-test",
            accessTokenTtl = 60.minutes,
            refreshTokenTtl = 24.hours,
        )
    private val service = JwtService(config)
    private val userId = Uuid.parse("00000000-0000-7000-8000-000000000001")
    private val issuedAt = Clock.System.now()

    @Test
    fun `an issued access token verifies back to the same user id and type`() {
        val token = service.issueAccessToken(userId, issuedAt)

        val verified = service.verify(token)

        assertEquals(userId, verified?.userId)
        assertEquals(TokenType.ACCESS, verified?.type)
        assertNull(verified?.sessionId)
        assertNull(verified?.familyId)
    }

    @Test
    fun `an issued refresh token carries its session and family ids`() {
        val sessionId = Uuid.parse("00000000-0000-7000-8000-000000000002")
        val familyId = Uuid.parse("00000000-0000-7000-8000-000000000003")

        val token = service.issueRefreshToken(userId, sessionId, familyId, issuedAt)
        val verified = service.verify(token)

        assertEquals(userId, verified?.userId)
        assertEquals(TokenType.REFRESH, verified?.type)
        assertEquals(sessionId, verified?.sessionId)
        assertEquals(familyId, verified?.familyId)
    }

    @Test
    fun `an already-expired access token fails verification`() {
        val longExpired = config.copy(accessTokenTtl = (-1).minutes)
        val expiredToken = JwtService(longExpired).issueAccessToken(userId, issuedAt)

        assertNull(service.verify(expiredToken))
    }

    @Test
    fun `a token signed with a different secret fails verification`() {
        val otherService = JwtService(config.copy(secret = "a-completely-different-secret"))
        val token = otherService.issueAccessToken(userId, issuedAt)

        assertNull(service.verify(token))
    }

    @Test
    fun `a token issued for a different audience fails verification`() {
        // Guards the same bug class as auth/Jwt.kt's configureAuthentication verifier: a token
        // minted for one audience must never verify against a service expecting another.
        val otherAudienceService = JwtService(config.copy(audience = "some-other-audience"))
        val token = otherAudienceService.issueAccessToken(userId, issuedAt)

        assertNull(service.verify(token))
    }

    @Test
    fun `a malformed token string fails verification rather than throwing`() {
        assertNull(service.verify("not.a.jwt"))
    }
}
