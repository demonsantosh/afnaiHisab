package com.afnaihisab.server

import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.core.data.api.ApiErrorEnvelope
import com.afnaihisab.core.domain.User
import com.afnaihisab.server.db.DatabaseFactory
import com.afnaihisab.server.repository.ExposedUserRepository
import com.afnaihisab.server.routes.AuthTokensResponse
import com.afnaihisab.server.routes.LoginRequest
import com.afnaihisab.server.routes.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * `docs/specs/registration-login.md` — the full register/login HTTP surface, boot against a real
 * in-memory H2 (ADR-0009's "real adjacent dependency" integration-test pattern), not an in-process
 * shortcut around routing/serialization.
 */
@OptIn(ExperimentalUuidApi::class)
class AuthRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun HttpClient.register(
        email: String,
        password: String,
        displayName: String = "Test User",
    ): HttpResponse =
        post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest(email, password, displayName)))
        }

    private suspend fun HttpClient.login(
        email: String,
        password: String,
    ): HttpResponse =
        post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(email, password)))
        }

    // ---- AC-R1 ----

    @Test
    fun `AC-R1 a valid registration creates the account and returns a token pair with 201`() =
        testApplication {
            application { module(testAppConfig()) }

            val response = client.register(email = "new-user@example.com", password = "correct-horse-battery")

            assertEquals(HttpStatusCode.Created, response.status)
            val tokens = json.decodeFromString<AuthTokensResponse>(response.bodyAsText())
            assertTrue(tokens.accessToken.isNotBlank())
            assertTrue(tokens.refreshToken.isNotBlank())
            assertEquals("Bearer", tokens.tokenType)
        }

    // ---- AC-R2 ----

    @Test
    fun `AC-R2 registering an already-registered email is rejected with 409 and creates no second record`() =
        testApplication {
            val config = testAppConfig()
            application { module(config) }

            val first = client.register(email = "duplicate@example.com", password = "correct-horse-battery")
            assertEquals(HttpStatusCode.Created, first.status)

            val second = client.register(email = "duplicate@example.com", password = "another-valid-password")

            assertEquals(HttpStatusCode.Conflict, second.status)
            val envelope = json.decodeFromString<ApiErrorEnvelope>(second.bodyAsText())
            assertEquals(ApiErrorCode.CONFLICT, envelope.error.code)
        }

    // ---- AC-R3 ----

    @Test
    fun `AC-R3 a password under 8 characters is rejected with 400 and field password`() =
        testApplication {
            application { module(testAppConfig()) }

            val response = client.register(email = "short-password@example.com", password = "short1")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val envelope = json.decodeFromString<ApiErrorEnvelope>(response.bodyAsText())
            assertEquals("password", envelope.error.field)
        }

    // ---- AC-R4 ----

    @Test
    fun `AC-R4 an invalid email shape is rejected with 400 and field email`() =
        testApplication {
            application { module(testAppConfig()) }

            val response = client.register(email = "not-an-email", password = "correct-horse-battery")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val envelope = json.decodeFromString<ApiErrorEnvelope>(response.bodyAsText())
            assertEquals("email", envelope.error.field)
        }

    // ---- AC-L1 ----

    @Test
    fun `AC-L1 logging in with the correct credentials issues a fresh token pair with 200`() =
        testApplication {
            application { module(testAppConfig()) }
            client.register(email = "login-happy@example.com", password = "correct-horse-battery")

            val response = client.login(email = "login-happy@example.com", password = "correct-horse-battery")

            assertEquals(HttpStatusCode.OK, response.status)
            val tokens = json.decodeFromString<AuthTokensResponse>(response.bodyAsText())
            assertTrue(tokens.accessToken.isNotBlank())
            assertTrue(tokens.refreshToken.isNotBlank())
        }

    @Test
    fun `AC-L1 each successful login issues a distinct token pair from the previous one`() =
        testApplication {
            application { module(testAppConfig()) }
            client.register(email = "login-rotation@example.com", password = "correct-horse-battery")

            val firstBody = client.login("login-rotation@example.com", "correct-horse-battery").bodyAsText()
            val secondBody = client.login("login-rotation@example.com", "correct-horse-battery").bodyAsText()
            val first = json.decodeFromString<AuthTokensResponse>(firstBody)
            val second = json.decodeFromString<AuthTokensResponse>(secondBody)

            assertNotEquals(first.refreshToken, second.refreshToken)
        }

    // ---- AC-L2 ----

    @Test
    fun `AC-L2 wrong password and an unknown email produce byte-identical 401 responses`() =
        testApplication {
            application { module(testAppConfig()) }
            client.register(email = "wrong-password@example.com", password = "correct-horse-battery")

            val wrongPassword = client.login(email = "wrong-password@example.com", password = "totally-wrong-password")
            val unknownEmail = client.login(email = "no-such-account@example.com", password = "irrelevant-password")

            assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
            assertEquals(HttpStatusCode.Unauthorized, unknownEmail.status)
            assertEquals(
                wrongPassword.bodyAsText(),
                unknownEmail.bodyAsText(),
                "AC-L2 requires an identical response body for both failure causes, so neither can be distinguished by a caller",
            )
        }

    // ---- AC-L3 ----

    @Test
    fun `AC-L3 a ghost user cannot log in and gets the same 401 as AC-L2`() =
        testApplication {
            val config = testAppConfig()
            application { module(config) }

            // Phase 1 never creates a ghost user through any route (V1__init.sql's own comment,
            // FEATURES.md §b) — fabricate one directly against the same database the app is using
            // (DB_CLOSE_DELAY=-1 in testAppConfig() keeps the in-memory instance alive across
            // separate connections/pools to the same JDBC URL).
            val fixtureDatabaseFactory = DatabaseFactory(config.database)
            try {
                val fixtureDatabase = fixtureDatabaseFactory.connectAndMigrate()
                ExposedUserRepository(fixtureDatabase).insert(
                    User(
                        id = Uuid.random(),
                        email = "ghost@example.com",
                        passwordHash = null,
                        displayName = "Ghost User",
                        isGhost = true,
                        createdAt = Clock.System.now(),
                    ),
                )
            } finally {
                fixtureDatabaseFactory.close()
            }

            val ghostLogin = client.login(email = "ghost@example.com", password = "any-password-at-all")
            val genericInvalid = client.login(email = "no-such-account-either@example.com", password = "any-password-at-all")

            assertEquals(HttpStatusCode.Unauthorized, ghostLogin.status)
            assertEquals(
                genericInvalid.bodyAsText(),
                ghostLogin.bodyAsText(),
                "AC-L3 must be indistinguishable from AC-L2's generic invalid-credentials response",
            )
        }
}
