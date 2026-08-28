package com.afnaihisab.server.routes

import com.afnaihisab.server.TEST_JWT_SECRET
import com.afnaihisab.server.auth.testJwt
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlin.uuid.Uuid

/**
 * Shared `testApplication`/`HttpClient` wiring for every route test in this package — every test
 * hits the real Ktor routing + Koin-wired repositories + H2 (ADR-0009's integration-test tier),
 * never an in-process call to a route function directly.
 */
fun HttpClientConfig<*>.jsonBody() {
    install(ContentNegotiation) { json() }
}

/** ADR-0008 bearer auth — [testJwt] mints a token signed with [TEST_JWT_SECRET] (see that file's KDoc). */
fun HttpRequestBuilder.bearer(userId: Uuid) {
    header(HttpHeaders.Authorization, "Bearer ${testJwt(userId)}")
}

/** ADR-0023's required header on every mutating request; a fresh random key per call unless reused deliberately. */
fun HttpRequestBuilder.idempotencyKey(key: Uuid = Uuid.random()) {
    header("Idempotency-Key", key.toString())
}
