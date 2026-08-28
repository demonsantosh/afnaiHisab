package com.afnaihisab.server.plugins

import com.afnaihisab.server.config.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.cors.routing.CORS

/**
 * How long a browser may cache an OPTIONS preflight. Without it, every single request is preceded
 * by its own preflight. 1 hour is conservative — the allow-list only changes via a deploy, never at
 * runtime.
 */
private const val PREFLIGHT_CACHE_SECONDS = 3600L

/**
 * Explicit allow-list, never `anyHost()` (ADR-0015).
 *
 * Dev is `http://localhost:3000` (Next.js); Phase 2 replaces it with the deployed web origin by
 * changing `CORS_ALLOWED_ORIGINS` in the environment — no code change.
 */
fun Application.configureCors(config: AppConfig) {
    require(config.corsAllowedOrigins.isNotEmpty()) {
        "CORS_ALLOWED_ORIGINS must list at least one origin; a wildcard is not an option (ADR-0015)"
    }

    install(CORS) {
        config.corsAllowedOrigins.forEach { origin ->
            val scheme = origin.substringBefore("://")
            val hostAndPort = origin.substringAfter("://").trimEnd('/')
            require(scheme.isNotBlank() && hostAndPort.isNotBlank() && scheme != origin) {
                "CORS origin '$origin' must be a full origin including scheme, e.g. http://localhost:3000"
            }
            allowHost(hostAndPort, schemes = listOf(scheme))
        }

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.ContentType)
        // Phase 1 sends the JWT access token as a Bearer header (ADR-0008), not a cookie — which is
        // also why `allowCredentials` stays off.
        allowHeader(HttpHeaders.Authorization)

        maxAgeInSeconds = PREFLIGHT_CACHE_SECONDS
    }

    log.info("CORS allow-list: {}", config.corsAllowedOrigins.joinToString())
}
