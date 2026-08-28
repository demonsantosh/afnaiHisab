package com.afnaihisab.server.routes

import com.afnaihisab.server.api.API_V1
import com.afnaihisab.server.auth.AUTH_JWT
import com.afnaihisab.server.health.HealthService
import com.afnaihisab.server.health.ServiceStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

/**
 * Every route is mounted under `/api/v1` (ADR-0015). `/health` and `/auth/register`+`/auth/login`
 * (`docs/specs/registration-login.md`) stay unauthenticated — a liveness probe and a not-yet-logged-in
 * client both have no token to present. Everything from `docs/specs/expense-split-balance-api.md`
 * requires a verified access-token JWT (ADR-0008).
 */
fun Application.configureRouting() {
    routing {
        route(API_V1) {
            healthRoutes()
            authRoutes()
            authenticate(AUTH_JWT) {
                ledgerRoutes()
                expenseRoutes()
                balanceRoutes()
                settlementRoutes()
            }
        }
    }
}

/**
 * `GET /api/v1/health` — 200 while every dependency is up, 503 once one is not, so a load balancer
 * or uptime check can act on the status code alone without parsing the body.
 */
fun Route.healthRoutes() {
    val healthService by inject<HealthService>()

    get("/health") {
        val report = healthService.check()
        val status =
            when (report.status) {
                ServiceStatus.OK -> HttpStatusCode.OK
                ServiceStatus.DEGRADED -> HttpStatusCode.ServiceUnavailable
            }
        call.respond(status, report)
    }
}
