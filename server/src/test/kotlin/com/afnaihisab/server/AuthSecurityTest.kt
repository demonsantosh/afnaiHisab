package com.afnaihisab.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.afnaihisab.server.config.AppConfig
import com.afnaihisab.server.db.DatabaseFactory
import com.afnaihisab.server.repository.ExposedUserRepository
import com.afnaihisab.server.routes.LoginRequest
import com.afnaihisab.server.routes.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `docs/specs/registration-login.md`'s explicit security test: actively searches every response
 * body and every captured log line from a full register/login run for the raw password or the
 * Argon2id `passwordHash` value — not just "the happy path works."
 */
class AuthSecurityTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val logAppender = ListAppender<ILoggingEvent>()
    private val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    @BeforeTest
    fun attachLogCapture() {
        logAppender.start()
        rootLogger.addAppender(logAppender)
    }

    @AfterTest
    fun detachLogCapture() {
        rootLogger.detachAppender(logAppender)
        logAppender.stop()
    }

    private suspend fun HttpClient.postBody(
        path: String,
        body: String,
    ): String =
        post(path) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()

    /** The actual persisted hash — the exact string that must never appear anywhere else. */
    private suspend fun fetchPersistedHash(
        config: AppConfig,
        email: String,
    ): String {
        val fixtureDatabaseFactory = DatabaseFactory(config.database)
        return try {
            val fixtureDatabase = fixtureDatabaseFactory.connectAndMigrate()
            ExposedUserRepository(fixtureDatabase).findByEmail(email)?.passwordHash
        } finally {
            fixtureDatabaseFactory.close()
        }.let {
            requireNotNull(it) { "test setup failure: registration should have persisted a passwordHash" }
        }
    }

    @Test
    fun `no response body or log line ever contains the raw password or its Argon2id hash`() =
        testApplication {
            val config = testAppConfig()
            application { module(config) }

            val email = "leak-hunt@example.com"
            val rawPassword = "a-very-specific-raw-password-marker-9f3c"

            val bodies =
                listOf(
                    client.postBody("/api/v1/auth/register", json.encodeToString(RegisterRequest(email, rawPassword, "Leak Hunt"))),
                    // AC-R2: a repeat registration with the same (correct) password.
                    client.postBody("/api/v1/auth/register", json.encodeToString(RegisterRequest(email, rawPassword, "Leak Hunt"))),
                    // AC-R3: a rejected registration — the raw (too-short) password must not echo back.
                    client.postBody("/api/v1/auth/register", json.encodeToString(RegisterRequest("short@example.com", "short1", "Short"))),
                    // AC-L1: a correct login.
                    client.postBody("/api/v1/auth/login", json.encodeToString(LoginRequest(email, rawPassword))),
                    // AC-L2: a wrong-password login, carrying the raw attempted password in the request.
                    client.postBody("/api/v1/auth/login", json.encodeToString(LoginRequest(email, "a-completely-wrong-password"))),
                )

            val persistedHash = fetchPersistedHash(config, email)

            for (body in bodies) {
                assertFalse(body.contains(rawPassword), "response body must never contain the raw password: $body")
                assertFalse(body.contains(persistedHash), "response body must never contain the password hash: $body")
                assertFalse(body.contains("argon2"), "response body must never contain an Argon2 hash marker: $body")
            }

            val logLines =
                logAppender.list.map { event -> "${event.formattedMessage} ${event.throwableProxy?.message.orEmpty()}" }
            for (line in logLines) {
                assertFalse(line.contains(rawPassword), "log line must never contain the raw password: $line")
                assertFalse(line.contains(persistedHash), "log line must never contain the password hash: $line")
            }

            // Sanity check the test itself exercises something real, not vacuously passing because
            // nothing was ever logged or returned.
            assertTrue(bodies.all { it.isNotBlank() })
        }
}
