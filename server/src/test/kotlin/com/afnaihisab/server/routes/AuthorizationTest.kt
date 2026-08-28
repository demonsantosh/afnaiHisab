package com.afnaihisab.server.routes

import com.afnaihisab.core.data.api.ApiErrorCode
import com.afnaihisab.core.data.api.ApiErrorEnvelope
import com.afnaihisab.server.module
import com.afnaihisab.server.routes.dto.AddMemberRequest
import com.afnaihisab.server.routes.dto.CreateExpenseRequest
import com.afnaihisab.server.routes.dto.CreateLedgerRequest
import com.afnaihisab.server.routes.dto.CreateSettlementRequest
import com.afnaihisab.server.routes.dto.LedgerResponse
import com.afnaihisab.server.seedUser
import com.afnaihisab.server.testAppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * ADR-0024's authorization check (AC-S1): a non-member is rejected `403` with the standard
 * envelope, for every ledger-scoped route this spec adds — one test per route, per ADR-0009's
 * amendment ("an explicit test that a non-member is rejected").
 */
class AuthorizationTest {
    private suspend fun HttpClient.createLedger(ownerId: Uuid): LedgerResponse =
        post("/api/v1/ledgers") {
            bearer(ownerId)
            idempotencyKey()
            contentType(ContentType.Application.Json)
            setBody(CreateLedgerRequest("Owner-only ledger", "NPR"))
        }.body()

    private fun assertForbidden(status: HttpStatusCode) {
        assertEquals(HttpStatusCode.Forbidden, status)
    }

    @Test
    fun `POST members is rejected for a non-member`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            val outsiderId = Uuid.random()
            seedUser(config, ownerId, "authz-owner1@example.com")
            seedUser(config, outsiderId, "authz-outsider1@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger = http.createLedger(ownerId)

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/members") {
                    bearer(outsiderId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(AddMemberRequest("nobody@example.com"))
                }

            assertForbidden(response.status)
            val envelope: ApiErrorEnvelope = response.body()
            assertEquals(ApiErrorCode.FORBIDDEN, envelope.error.code)
        }

    @Test
    fun `POST expenses is rejected for a non-member`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            val outsiderId = Uuid.random()
            seedUser(config, ownerId, "authz-owner2@example.com")
            seedUser(config, outsiderId, "authz-outsider2@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger = http.createLedger(ownerId)

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(outsiderId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateExpenseRequest(
                            payerMembershipId = ledger.ownerMembershipId,
                            amount = 100,
                            currency = "NPR",
                            category = "food",
                            date = "2026-08-28",
                        ),
                    )
                }

            assertForbidden(response.status)
        }

    @Test
    fun `GET expenses is rejected for a non-member`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            val outsiderId = Uuid.random()
            seedUser(config, ownerId, "authz-owner3@example.com")
            seedUser(config, outsiderId, "authz-outsider3@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger = http.createLedger(ownerId)

            val response = http.get("/api/v1/ledgers/${ledger.id}/expenses") { bearer(outsiderId) }

            assertForbidden(response.status)
        }

    @Test
    fun `GET balances is rejected for a non-member`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            val outsiderId = Uuid.random()
            seedUser(config, ownerId, "authz-owner4@example.com")
            seedUser(config, outsiderId, "authz-outsider4@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger = http.createLedger(ownerId)

            val response = http.get("/api/v1/ledgers/${ledger.id}/balances") { bearer(outsiderId) }

            assertForbidden(response.status)
        }

    @Test
    fun `POST settlements is rejected for a non-member`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            val outsiderId = Uuid.random()
            seedUser(config, ownerId, "authz-owner5@example.com")
            seedUser(config, outsiderId, "authz-outsider5@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger = http.createLedger(ownerId)

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/settlements") {
                    bearer(outsiderId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateSettlementRequest(
                            fromMembershipId = ledger.ownerMembershipId,
                            toMembershipId = ledger.ownerMembershipId,
                            amount = 100,
                            currency = "NPR",
                        ),
                    )
                }

            assertForbidden(response.status)
        }
}
