package com.afnaihisab.server

import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ADR-0015's CORS allow-list is a security boundary, not a convenience setting — a regression to
 * `anyHost()` would be invisible without a test that a non-listed origin is actually refused.
 */
class CorsTest {
    @Test
    fun `preflight from the allowed web origin is accepted`() =
        testApplication {
            application { module(testAppConfig()) }

            val response =
                client.options("/api/v1/health") {
                    header(HttpHeaders.Origin, ALLOWED_WEB_ORIGIN)
                    header(HttpHeaders.AccessControlRequestMethod, "GET")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ALLOWED_WEB_ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `preflight from an origin outside the allow-list is refused`() =
        testApplication {
            application { module(testAppConfig()) }

            val response =
                client.options("/api/v1/health") {
                    header(HttpHeaders.Origin, "http://evil.example.com")
                    header(HttpHeaders.AccessControlRequestMethod, "GET")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
}
