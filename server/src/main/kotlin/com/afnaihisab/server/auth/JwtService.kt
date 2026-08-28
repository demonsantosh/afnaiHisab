package com.afnaihisab.server.auth

import com.afnaihisab.server.config.JwtConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import java.util.Date as JavaDate

/** The `type` claim distinguishing an access token from a refresh token (ADR-0008). */
enum class TokenType {
    ACCESS,
    REFRESH,
}

/** A verified, decoded token's claims this service's callers actually need. */
data class VerifiedToken(
    val userId: Uuid,
    val type: TokenType,
    /** Only meaningful for [TokenType.REFRESH] — the `refresh_sessions` row id (rotation, ADR-0008). */
    val sessionId: Uuid?,
    /** Only meaningful for [TokenType.REFRESH] — the session family id (reuse detection, ADR-0008). */
    val familyId: Uuid?,
)

/**
 * Issues and verifies the signed JWTs ADR-0008 decided on: a short-lived access token and a
 * longer-lived refresh token, both HMAC-signed with [JwtConfig.secret] and scoped to
 * [JwtConfig.audience] — the same audience `auth/Jwt.kt`'s [configureAuthentication] verifier
 * requires of every bearer token presented to a protected route, so a token issued here is actually
 * accepted there (the audience mismatch this fixes: a token minted without an audience claim would
 * otherwise be rejected by every `authenticate(AUTH_JWT) { }` route).
 *
 * The refresh token is a signed *carrier* for its `refresh_sessions` row id (`jti`) and family id
 * (`fam`) — [RefreshSessionRepository] is the actual source of truth for whether a refresh token
 * is still valid (unused, unrevoked, unexpired); this service only proves the token wasn't forged
 * or tampered with and hasn't outlived its own `exp`. Rotation and reuse-detection live in
 * [RefreshSessionRepository], not here — this class has no database access (ADR-0001: no I/O in a
 * pure signing/verification utility either, mirroring `core`'s own discipline even though this
 * class lives in `server`).
 */
@OptIn(ExperimentalUuidApi::class)
class JwtService(
    private val config: JwtConfig,
) {
    /** Exposed so a caller (e.g. [AuthService]) doesn't need its own separate [JwtConfig] reference. */
    val accessTokenTtl get() = config.accessTokenTtl

    val refreshTokenTtl get() = config.refreshTokenTtl

    private val algorithm = Algorithm.HMAC256(config.secret)

    private val verifier =
        JWT
            .require(algorithm)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()

    fun issueAccessToken(
        userId: Uuid,
        issuedAt: Instant,
    ): String =
        JWT
            .create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId.toString())
            .withClaim(CLAIM_TYPE, TokenType.ACCESS.name)
            .withIssuedAt(issuedAt.toJavaDate())
            .withExpiresAt((issuedAt + config.accessTokenTtl).toJavaDate())
            .sign(algorithm)

    fun issueRefreshToken(
        userId: Uuid,
        sessionId: Uuid,
        familyId: Uuid,
        issuedAt: Instant,
    ): String =
        JWT
            .create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId.toString())
            .withClaim(CLAIM_TYPE, TokenType.REFRESH.name)
            .withClaim(CLAIM_SESSION_ID, sessionId.toString())
            .withClaim(CLAIM_FAMILY_ID, familyId.toString())
            .withIssuedAt(issuedAt.toJavaDate())
            .withExpiresAt((issuedAt + config.refreshTokenTtl).toJavaDate())
            .sign(algorithm)

    /** `null` for a token that's malformed, has an invalid signature, or has expired. */
    fun verify(token: String): VerifiedToken? =
        runCatching { verifier.verify(token) }
            .getOrNull()
            ?.let(::toVerifiedToken)

    // Written as a chain of nullable `let`s rather than a sequence of `return null` guards
    // (detekt's ReturnCount) — each helper below owns one small piece of "parse or give up."
    private fun toVerifiedToken(decoded: DecodedJWT): VerifiedToken? =
        decoded.tokenType()?.let { type ->
            decoded.parsedSubject()?.let { userId ->
                decoded.buildVerifiedToken(userId, type)
            }
        }

    private fun DecodedJWT.tokenType(): TokenType? =
        getClaim(CLAIM_TYPE).asString()?.let { runCatching { TokenType.valueOf(it) }.getOrNull() }

    private fun DecodedJWT.parsedSubject(): Uuid? = runCatching { Uuid.parse(subject) }.getOrNull()

    private fun DecodedJWT.parsedClaimUuid(claimName: String): Uuid? =
        getClaim(claimName).asString()?.let { runCatching { Uuid.parse(it) }.getOrNull() }

    private fun DecodedJWT.buildVerifiedToken(
        userId: Uuid,
        type: TokenType,
    ): VerifiedToken? =
        when (type) {
            TokenType.ACCESS -> VerifiedToken(userId, type, sessionId = null, familyId = null)
            TokenType.REFRESH ->
                parsedClaimUuid(CLAIM_SESSION_ID)?.let { sessionId ->
                    parsedClaimUuid(CLAIM_FAMILY_ID)?.let { familyId ->
                        VerifiedToken(userId, type, sessionId, familyId)
                    }
                }
        }

    private companion object {
        const val CLAIM_TYPE = "type"
        const val CLAIM_SESSION_ID = "sid"
        const val CLAIM_FAMILY_ID = "fam"
    }
}

/** The java-jwt library's API is `java.util.Date`-based; this is the one place that boundary is crossed. */
private fun Instant.toJavaDate(): JavaDate = JavaDate(toEpochMilliseconds())
