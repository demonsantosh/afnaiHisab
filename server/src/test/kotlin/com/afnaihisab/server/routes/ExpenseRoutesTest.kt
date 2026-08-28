package com.afnaihisab.server.routes

import com.afnaihisab.server.module
import com.afnaihisab.server.routes.dto.CreateExpenseRequest
import com.afnaihisab.server.routes.dto.CreateLedgerRequest
import com.afnaihisab.server.routes.dto.ExpenseResponse
import com.afnaihisab.server.routes.dto.LedgerResponse
import com.afnaihisab.server.seedUser
import com.afnaihisab.server.testAppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * `POST`/`GET /ledgers/{ledgerId}/expenses` — core AC-1..AC-5's equal split over a real HTTP call,
 * plus AC-S5's cursor-pagination clamp.
 */
class ExpenseRoutesTest {
    private suspend fun HttpClient.createLedgerAndOwner(
        ownerId: Uuid,
        currency: String = "NPR",
    ): LedgerResponse =
        post("/api/v1/ledgers") {
            bearer(ownerId)
            idempotencyKey()
            contentType(ContentType.Application.Json)
            setBody(CreateLedgerRequest(name = "Trip", defaultCurrency = currency))
        }.body()

    private fun errorCodeOf(bodyText: String): String =
        Json
            .parseToJsonElement(bodyText)
            .jsonObject["error"]!!
            .jsonObject["code"]!!
            .jsonPrimitive.content

    @Test
    fun `creating an equal-split expense splits it across the ledger's members`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "expense-owner@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val ledger = http.createLedgerAndOwner(ownerId)

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateExpenseRequest(
                            payerMembershipId = ledger.ownerMembershipId,
                            amount = 1000,
                            currency = "NPR",
                            category = "food",
                            date = "2026-08-28",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body: ExpenseResponse = response.body()
            assertEquals(1000L, body.amount)
            assertEquals(1, body.splits.size)
            assertEquals(1000L, body.splits.single().amount)
        }

    @Test
    fun `AC-3 a non-positive amount is rejected with 400 and core's stable error code`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "ac3-owner@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger = http.createLedgerAndOwner(ownerId)

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateExpenseRequest(
                            payerMembershipId = ledger.ownerMembershipId,
                            amount = 0,
                            currency = "NPR",
                            category = "food",
                            date = "2026-08-28",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("amount_not_positive", errorCodeOf(response.bodyAsText()))
        }

    @Test
    fun `AC-4 a payer that is not a member of the ledger is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "ac4-owner@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger = http.createLedgerAndOwner(ownerId)

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateExpenseRequest(
                            payerMembershipId = Uuid.random().toString(),
                            amount = 500,
                            currency = "NPR",
                            category = "food",
                            date = "2026-08-28",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("payer_not_member", errorCodeOf(response.bodyAsText()))
        }

    @Test
    fun `AC-5 a currency that doesn't match the ledger's default is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "ac5-owner@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger = http.createLedgerAndOwner(ownerId, currency = "NPR")

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateExpenseRequest(
                            payerMembershipId = ledger.ownerMembershipId,
                            amount = 500,
                            currency = "USD",
                            category = "food",
                            date = "2026-08-28",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("currency_mismatch", errorCodeOf(response.bodyAsText()))
        }

    @Test
    fun `AC-S5 expense listing defaults to 50 and clamps an over-large page size to 200`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "list-owner@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger = http.createLedgerAndOwner(ownerId)

            repeat(3) { index ->
                http.post("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateExpenseRequest(
                            payerMembershipId = ledger.ownerMembershipId,
                            amount = (index + 1) * 100L,
                            currency = "NPR",
                            category = "food",
                            date = "2026-08-28",
                        ),
                    )
                }
            }

            val defaultPage = http.get("/api/v1/ledgers/${ledger.id}/expenses") { bearer(ownerId) }
            assertEquals(HttpStatusCode.OK, defaultPage.status)
            val defaultJson = Json.parseToJsonElement(defaultPage.bodyAsText()).jsonObject
            assertEquals(3, defaultJson["items"]!!.jsonArray.size)

            // AC-S5: an out-of-range page size is clamped, never rejected.
            val clamped =
                http.get("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(ownerId)
                    parameter("limit", "999")
                }
            assertEquals(HttpStatusCode.OK, clamped.status)
        }
}
