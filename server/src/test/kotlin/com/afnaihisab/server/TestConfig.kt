package com.afnaihisab.server

import com.afnaihisab.server.config.AppConfig
import com.afnaihisab.server.config.DatabaseConfig
import com.afnaihisab.server.config.JwtConfig
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

const val ALLOWED_WEB_ORIGIN: String = "http://localhost:3000"

/**
 * Fixed test-only JWT signing secret (ADR-0008). Registration/login are not built yet
 * (`docs/specs/expense-split-balance-api.md` scopes them out) — [testAppConfig] and
 * `server/src/test/.../auth/TestJwt.kt`'s `testJwt(...)` both reference this one constant so a
 * token minted for a test can always be verified by a server booted with [testAppConfig].
 */
const val TEST_JWT_SECRET: String = "test-only-jwt-signing-secret-never-used-outside-this-test-suite"
const val TEST_JWT_ISSUER: String = "afnaihisab-test"
const val TEST_JWT_AUDIENCE: String = "afnaihisab-test-clients"

/**
 * A config pointed at a private in-memory H2 — same PostgreSQL compatibility mode as local dev, so
 * `V1__init.sql` is exercised by every test run rather than only by whoever runs the app.
 *
 * A fresh database name per call keeps tests independent (ADR-0009).
 */
fun testAppConfig(): AppConfig =
    AppConfig(
        environment = "test",
        host = "127.0.0.1",
        port = 0,
        database =
            DatabaseConfig(
                url =
                    "jdbc:h2:mem:afnaihisab-${UUID.randomUUID()};" +
                        "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                user = "sa",
                password = "",
                maxPoolSize = 2,
            ),
        corsAllowedOrigins = listOf(ALLOWED_WEB_ORIGIN),
        jwt =
            JwtConfig(
                secret = TEST_JWT_SECRET,
                issuer = TEST_JWT_ISSUER,
                audience = TEST_JWT_AUDIENCE,
                realm = "afnaihisab-test",
                accessTokenTtl = 60.minutes,
                refreshTokenTtl = 24.hours,
            ),
    )
