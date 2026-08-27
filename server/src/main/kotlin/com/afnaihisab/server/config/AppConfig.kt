package com.afnaihisab.server.config

import io.github.cdimascio.dotenv.dotenv

/**
 * All runtime configuration, loaded once at startup.
 *
 * Source of values (ADR-0015 — "secrets: `.env` (gitignored) for local dev"):
 * 1. a real environment variable, if set (this is what CI and any deployed environment use);
 * 2. otherwise the repo-root `.env` file, if present;
 * 3. otherwise the default below.
 *
 * `.env` is gitignored; `.env.example` documents every key and is committed. Nothing secret is
 * ever hardcoded here — a default only exists where the value is not a secret (ports, the local
 * H2 path, the dev CORS origin).
 */
data class AppConfig(
    val environment: String,
    val host: String,
    val port: Int,
    val database: DatabaseConfig,
    /** Explicit allow-list, never a wildcard (ADR-0015). Full origins, e.g. `http://localhost:3000`. */
    val corsAllowedOrigins: List<String>,
) {
    val isDevelopment: Boolean get() = environment == "development"

    companion object {
        fun load(): AppConfig {
            val env =
                dotenv {
                    ignoreIfMissing = true
                }

            fun value(
                key: String,
                default: String,
            ): String = env[key]?.takeIf { it.isNotBlank() } ?: default

            return AppConfig(
                environment = value("APP_ENV", "development"),
                host = value("SERVER_HOST", "0.0.0.0"),
                port = value("SERVER_PORT", "8080").toInt(),
                database =
                    DatabaseConfig(
                        url = value("DATABASE_URL", DatabaseConfig.LOCAL_H2_URL),
                        user = value("DATABASE_USER", "sa"),
                        password = env["DATABASE_PASSWORD"] ?: "",
                        maxPoolSize = value("DATABASE_POOL_SIZE", "5").toInt(),
                    ),
                corsAllowedOrigins =
                    value("CORS_ALLOWED_ORIGINS", "http://localhost:3000")
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
            )
        }
    }
}

/**
 * @property url a JDBC URL. Phase 0 defaults to file-backed H2 in Postgres compatibility mode so
 *   the stack runs with no Docker install (`docs/TOOLING.md`); pointing this at a real Postgres is
 *   a one-line `.env` change, which is the whole reason for the compatibility mode.
 */
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
) {
    val isH2: Boolean get() = url.startsWith("jdbc:h2:")

    companion object {
        /**
         * `MODE=PostgreSQL` + `DATABASE_TO_LOWER=TRUE` make H2 accept the same unquoted,
         * lowercase-identifier SQL that Postgres does, so `V1__init.sql` is one file for both.
         * `AUTO_SERVER=TRUE` lets a second process (a DB viewer) attach while the server runs.
         */
        const val LOCAL_H2_URL =
            "jdbc:h2:file:./.data/afnaihisab;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
    }
}
