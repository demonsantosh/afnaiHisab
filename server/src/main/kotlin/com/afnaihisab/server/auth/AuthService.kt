package com.afnaihisab.server.auth

import com.afnaihisab.core.domain.User
import com.afnaihisab.core.domain.validateRegistration
import com.afnaihisab.core.validation.ValidationError
import com.afnaihisab.core.validation.ValidationResult
import com.afnaihisab.server.repository.UserRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The token pair every successful register/login issues (ADR-0008). */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
)

sealed interface RegisterResult {
    data class Success(
        val user: User,
        val tokens: AuthTokens,
    ) : RegisterResult

    /** AC-R2/AC-R3/AC-R4 — validation errors always precede the uniqueness check (see [AuthService.register]). */
    data class Rejected(
        val errors: List<ValidationError>,
    ) : RegisterResult

    /** AC-R2 — email already registered to a non-ghost user. */
    data object EmailAlreadyRegistered : RegisterResult
}

sealed interface LoginResult {
    data class Success(
        val user: User,
        val tokens: AuthTokens,
    ) : LoginResult

    /**
     * AC-L2 (wrong password or no matching non-ghost user) and AC-L3 (ghost user) both land here —
     * deliberately one variant, not three, so a route mapping this to HTTP cannot accidentally
     * produce a different response per cause (the email-enumeration leak AC-L2 exists to prevent).
     */
    data object InvalidCredentials : LoginResult
}

/**
 * Orchestrates registration/login (`docs/specs/registration-login.md`): the actual account-creation
 * and token-issuance workflow, kept out of the route handler per the Ktor/backend guideline
 * ("routes are thin ... no business logic beyond calling a repository") — a route calls one method
 * here and maps the sealed result to a response, nothing else.
 *
 * Password hashing (ADR-0030) and JWT issuance (ADR-0008) are `server`-only concerns that cannot
 * live in `core` (`core` never sees a raw password or a hash); the one rule that *is* shareable
 * business logic — email shape and password-length validation — is delegated to
 * [validateRegistration] in `core`, not reimplemented here.
 */
@OptIn(ExperimentalUuidApi::class)
class AuthService(
    private val userRepository: UserRepository,
    private val refreshSessionRepository: RefreshSessionRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtService: JwtService,
    private val now: () -> Instant = { Clock.System.now() },
    private val newId: () -> Uuid = { Uuid.random() },
) {
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
    ): RegisterResult {
        val validation = validateRegistration(email, password)
        if (validation is ValidationResult.Invalid) {
            return RegisterResult.Rejected(validation.errors)
        }

        // AC-R2: the fast path. `ux_users_email` (V1__init.sql) is the actual, DB-enforced
        // guarantee against a concurrent duplicate registration racing past this check — see
        // UserRepository.insert's KDoc.
        return if (userRepository.findNonGhostByEmail(email) != null) {
            RegisterResult.EmailAlreadyRegistered
        } else {
            val user =
                User(
                    id = newId(),
                    email = email,
                    passwordHash = passwordHasher.hash(password),
                    displayName = displayName,
                    isGhost = false,
                    createdAt = now(),
                )
            val inserted = userRepository.insert(user)
            RegisterResult.Success(inserted, issueTokens(inserted.id))
        }
    }

    suspend fun login(
        email: String,
        password: String,
    ): LoginResult {
        val user = userRepository.findByEmail(email)
        val hash = user?.passwordHash

        // AC-L3: a ghost user has no passwordHash and can never log in. AC-L2: no matching user.
        // Both paths still run a real (dummy) Argon2id verification so the response-time profile
        // doesn't itself leak which of the two happened (docs/specs/registration-login.md AC-L2).
        if (user == null || user.isGhost || hash == null) {
            passwordHasher.verifyAgainstDummy(password)
            return LoginResult.InvalidCredentials
        }

        return if (!passwordHasher.verify(password, hash)) {
            LoginResult.InvalidCredentials
        } else {
            LoginResult.Success(user, issueTokens(user.id))
        }
    }

    /** Issues the access token plus a brand-new refresh session family (AC-R1/AC-L1). */
    private suspend fun issueTokens(userId: Uuid): AuthTokens {
        val issuedAt = now()
        val sessionId = newId()
        val familyId = newId()
        val accessExpiresAt = issuedAt + jwtService.accessTokenTtl
        val refreshExpiresAt = issuedAt + jwtService.refreshTokenTtl

        refreshSessionRepository.issue(
            userId = userId,
            sessionId = sessionId,
            familyId = familyId,
            issuedAt = issuedAt,
            expiresAt = refreshExpiresAt,
        )

        return AuthTokens(
            accessToken = jwtService.issueAccessToken(userId, issuedAt),
            refreshToken = jwtService.issueRefreshToken(userId, sessionId, familyId, issuedAt),
            accessTokenExpiresAt = accessExpiresAt,
            refreshTokenExpiresAt = refreshExpiresAt,
        )
    }
}
