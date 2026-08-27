package com.afnaihisab.server

import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.core.data.api.ApiErrorEnvelope
import com.afnaihisab.server.health.ComponentStatus
import com.afnaihisab.server.health.HealthResponse
import com.afnaihisab.server.health.ServiceStatus
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Boots the real application module against an in-memory database (ADR-0009 —
 * `ktor-server-test-host` for route/integration coverage).
 */
class HealthRouteTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `health endpoint is served under the api v1 prefix and reports the database as up`() =
        testApplication {
            application { module(testAppConfig()) }

            val response = client.get("/api/v1/health")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<HealthResponse>(response.bodyAsText())
            assertEquals(ServiceStatus.OK, body.status)
            assertEquals(ComponentStatus.UP, body.database)
            assertEquals(BuildInfo.SERVICE_NAME, body.service)
        }

    @Test
    fun `health endpoint is not exposed outside the version prefix`() =
        testApplication {
            application { module(testAppConfig()) }

            assertEquals(HttpStatusCode.NotFound, client.get("/health").status)
        }

    @Test
    fun `an unmatched route returns the standard error envelope`() =
        testApplication {
            application { module(testAppConfig()) }

            val response = client.get("/api/v1/does-not-exist")

            assertEquals(HttpStatusCode.NotFound, response.status)
            val envelope = json.decodeFromString<ApiErrorEnvelope>(response.bodyAsText())
            assertEquals(ApiErrorCode.NOT_FOUND, envelope.error.code)
        }
}
