package com.afnaihisab.server

import com.afnaihisab.server.config.AppConfig
import com.afnaihisab.server.config.DatabaseConfig
import java.util.UUID

const val ALLOWED_WEB_ORIGIN: String = "http://localhost:3000"

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
    )
