package com.afnaihisab.server.auth

import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.server.api.respondError
import com.afnaihisab.server.config.AppConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import kotlin.uuid.Uuid

/**
 * The name every `authenticate(...)` block in the `routes` package references.
 *
 * This configures token *verification* for `AuthService`/`JwtService`-issued tokens
 * (`docs/specs/registration-login.md`, ADR-0008) using the same [AppConfig.jwt] secret they sign
 * with. Server tests mint tokens directly (`server/src/test/.../auth/TestJwt.kt`) rather than
 * calling `/auth/login`, for routes whose own spec doesn't otherwise need a real login round-trip.
 */
const val AUTH_JWT: String = "auth-jwt"

/** The claim name [JwtService] stamps on every issued token — must match [JwtService]'s own constant. */
private const val CLAIM_TYPE = "type"

/** The only token type this plugin's `validate` block accepts — a refresh token must never pass. */
private const val ACCESS_TOKEN_TYPE = "ACCESS"

/**
 * Installs JWT verification (ADR-0008). A request without a valid, unexpired, correctly-signed
 * bearer token *of type [ACCESS_TOKEN_TYPE]* never reaches a route handler wrapped in
 * `authenticate(AUTH_JWT) { }` — it gets ADR-0015's standard error envelope via [challenge], not
 * Ktor's default `WWW-Authenticate` body.
 *
 * The `type` claim check exists because [JwtService] issues both access and refresh tokens signed
 * with the same secret/issuer/audience — without it, a refresh token (which is only ever meant to
 * be presented to a future `/refresh` endpoint) could be replayed as a bearer access token against
 * every protected route.
 */
fun Application.configureAuthentication(config: AppConfig) {
    val verifier =
        JWT
            .require(Algorithm.HMAC256(config.jwt.secret))
            .withIssuer(config.jwt.issuer)
            .withAudience(config.jwt.audience)
            .build()

    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = config.jwt.realm
            verifier(verifier)
            validate { credential ->
                val subject = credential.payload.subject
                val hasValidSubject = subject != null && runCatching { Uuid.parse(subject) }.isSuccess
                val isAccessToken = credential.payload.getClaim(CLAIM_TYPE).asString() == ACCESS_TOKEN_TYPE
                if (hasValidSubject && isAccessToken) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respondError(
                    status = HttpStatusCode.Unauthorized,
                    code = ApiErrorCode.UNAUTHORIZED,
                    message = "A valid access token is required.",
                )
            }
        }
    }
}

/**
 * The authenticated caller's [com.afnaihisab.core.domain.User.id], parsed from the verified JWT's
 * `sub` claim.
 *
 * Only valid inside a route wrapped in `authenticate(AUTH_JWT) { }` — [configureAuthentication]'s
 * `validate` block already guarantees `sub` parses as a [Uuid] before a principal is ever created,
 * so this never throws there.
 */
fun ApplicationCall.authenticatedUserId(): Uuid {
    val principal = requireNotNull(principal<JWTPrincipal>()) { "authenticatedUserId() called outside an authenticate(AUTH_JWT) block" }
    return Uuid.parse(requireNotNull(principal.payload.subject) { "JWT principal missing its subject claim" })
}
