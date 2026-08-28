package com.afnaihisab.server.routes

import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.server.api.respondError
import com.afnaihisab.server.auth.AuthService
import com.afnaihisab.server.auth.AuthTokens
import com.afnaihisab.server.auth.LoginResult
import com.afnaihisab.server.auth.RegisterResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

/**
 * The wire shape of a successful register/login (`docs/specs/registration-login.md` AC-R1, AC-L1).
 * `expiresAt` values are ISO-8601 UTC instants, matching `HealthResponse`'s existing convention.
 */
@Serializable
data class AuthTokensResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val accessTokenExpiresAt: String,
    val refreshTokenExpiresAt: String,
)

private fun AuthTokens.toResponse() =
    AuthTokensResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = "Bearer",
        accessTokenExpiresAt = accessTokenExpiresAt.toString(),
        refreshTokenExpiresAt = refreshTokenExpiresAt.toString(),
    )

/**
 * `POST /api/v1/auth/register` and `POST /api/v1/auth/login`
 * (`docs/specs/registration-login.md`) — the genuine entry point into the app. Every rule this
 * route enforces lives in [AuthService]/`core`'s `validateRegistration`; this function only
 * deserializes, calls that service, and maps its sealed result to a response (ADR-0001, the
 * Ktor/backend guideline — routes are thin).
 */
fun Route.authRoutes() {
    val authService by inject<AuthService>()

    post("/auth/register") {
        val request = call.receive<RegisterRequest>()
        when (val result = authService.register(request.email, request.password, request.displayName)) {
            is RegisterResult.Success -> call.respond(HttpStatusCode.Created, result.tokens.toResponse())

            // AC-R2 — creates no record; the response carries no field, only a conflict.
            RegisterResult.EmailAlreadyRegistered ->
                call.respondError(
                    status = HttpStatusCode.Conflict,
                    code = ApiErrorCode.CONFLICT,
                    message = "An account with this email already exists.",
                )

            // AC-R3/AC-R4 — the envelope carries one field; see ApiError's KDoc for why only the
            // first accumulated violation is surfaced here.
            is RegisterResult.Rejected -> {
                val error = result.errors.first()
                call.respondError(
                    status = HttpStatusCode.BadRequest,
                    code = ApiErrorCode.VALIDATION_FAILED,
                    message = error.message,
                    field = error.field,
                )
            }
        }
    }

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        when (val result = authService.login(request.email, request.password)) {
            is LoginResult.Success -> call.respond(HttpStatusCode.OK, result.tokens.toResponse())

            // AC-L2/AC-L3 — deliberately the exact same status/code/message/field for both causes,
            // so the response body is byte-identical regardless of which one applied.
            LoginResult.InvalidCredentials ->
                call.respondError(
                    status = HttpStatusCode.Unauthorized,
                    code = ApiErrorCode.UNAUTHORIZED,
                    message = "Invalid email or password.",
                )
        }
    }
}
