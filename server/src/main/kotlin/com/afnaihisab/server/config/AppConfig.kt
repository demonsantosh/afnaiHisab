package com.afnaihisab.server.config

import io.github.cdimascio.dotenv.dotenv
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
    /**
     * JWT issuance + verification config (ADR-0008). [JwtService] (`docs/specs/registration-login.md`)
     * issues access/refresh tokens with this config; [configureAuthentication]'s verifier (used by
     * every `authenticate(AUTH_JWT) { }` route) checks a bearer token against the same secret,
     * issuer, and audience.
     */
    val jwt: JwtConfig,
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

            val jwtSecret = value("JWT_SECRET", JwtConfig.INSECURE_DEV_SECRET)
            val environment = value("APP_ENV", "development")
            require(!(environment == "production" && jwtSecret == JwtConfig.INSECURE_DEV_SECRET)) {
                "JWT_SECRET must be set explicitly in production (ADR-0015) — the dev default is never safe there."
            }

            return AppConfig(
                environment = environment,
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
                jwt =
                    JwtConfig(
                        secret = jwtSecret,
                        issuer = value("JWT_ISSUER", "afnaihisab"),
                        audience = value("JWT_AUDIENCE", "afnaihisab-clients"),
                        realm = value("JWT_REALM", "afnaihisab"),
                        accessTokenTtl = value("JWT_ACCESS_TOKEN_TTL_MINUTES", "60").toLong().minutes,
                        refreshTokenTtl = value("JWT_REFRESH_TOKEN_TTL_HOURS", "24").toLong().hours,
                    ),
            )
        }
    }
}

/**
 * ADR-0008 (access ~1h / refresh ~24h+ expiry) + ADR-0015 (secrets via `.env`/real env var, never
 * hardcoded for a real environment).
 *
 * @property secret HMAC256 signing key, shared by [JwtService] issuance and
 *   [configureAuthentication]'s verifier (ADR-0015 — never hardcoded for real use;
 *   [INSECURE_DEV_SECRET] exists only so local dev/test runs without a `.env` entry). If this is
 *   ever suspected compromised, ADR-0015's "break glass" rotation procedure applies: replace it,
 *   which invalidates every existing session at once.
 * @property audience checked by [configureAuthentication]'s verifier on every protected route — a
 *   token [JwtService] issues without this exact audience claim would otherwise be rejected there.
 * @property realm the WWW-Authenticate realm [configureAuthentication] reports on a 401.
 */
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
) {
    companion object {
        /**
         * Obviously-labelled, never-for-production fallback so Phase 0/1 local dev keeps working
         * with no `.env` entry, same ergonomics as [DatabaseConfig.LOCAL_H2_URL]. [AppConfig.load]
         * refuses to start with this value in a `production` environment.
         */
        const val INSECURE_DEV_SECRET: String =
            "insecure-development-only-jwt-signing-secret-do-not-use-in-production"
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
