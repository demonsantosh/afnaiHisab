package com.afnaihisab.server.routes

import com.afnaihisab.server.module
import com.afnaihisab.server.routes.dto.AddMemberRequest
import com.afnaihisab.server.routes.dto.BalancesResponse
import com.afnaihisab.server.routes.dto.CreateExpenseRequest
import com.afnaihisab.server.routes.dto.CreateLedgerRequest
import com.afnaihisab.server.routes.dto.CreateSettlementRequest
import com.afnaihisab.server.routes.dto.LedgerResponse
import com.afnaihisab.server.routes.dto.MembershipResponse
import com.afnaihisab.server.routes.dto.SettlementResponse
import com.afnaihisab.server.seedUser
import com.afnaihisab.server.testAppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * `GET /ledgers/{ledgerId}/balances` (core AC-6, AC-7) and
 * `POST /ledgers/{ledgerId}/settlements` (core AC-8..AC-10, AC-13), over real HTTP calls.
 */
class BalanceAndSettlementRoutesTest {
    private fun errorCodeOf(bodyText: String): String =
        Json
            .parseToJsonElement(bodyText)
            .jsonObject["error"]!!
            .jsonObject["code"]!!
            .jsonPrimitive.content

    /** A ledger with an owner, one added member, and a single 1000-unit expense the owner paid. */
    private suspend fun HttpClient.ledgerWithExpense(
        ownerId: Uuid,
        friendEmail: String,
    ): Pair<LedgerResponse, MembershipResponse> {
        val ledger =
            post("/api/v1/ledgers") {
                bearer(ownerId)
                idempotencyKey()
                contentType(ContentType.Application.Json)
                setBody(CreateLedgerRequest("Trip", "NPR"))
            }.body<LedgerResponse>()
        val friendMembership =
            post("/api/v1/ledgers/${ledger.id}/members") {
                bearer(ownerId)
                idempotencyKey()
                contentType(ContentType.Application.Json)
                setBody(AddMemberRequest(friendEmail))
            }.body<MembershipResponse>()
        post("/api/v1/ledgers/${ledger.id}/expenses") {
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
        return ledger to friendMembership
    }

    @Test
    fun `balances reflect an expense, and a full settlement zeroes both parties (AC-6, AC-7, AC-13)`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            val friendId = Uuid.random()
            seedUser(config, ownerId, "bs-owner@example.com")
            seedUser(config, friendId, "bs-friend@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val (ledger, friendMembership) = http.ledgerWithExpense(ownerId, "bs-friend@example.com")

            val before = http.get("/api/v1/ledgers/${ledger.id}/balances") { bearer(ownerId) }.body<BalancesResponse>()
            assertEquals(500L, before.balances.single { it.membershipId == ledger.ownerMembershipId }.netBalance)
            assertEquals(-500L, before.balances.single { it.membershipId == friendMembership.id }.netBalance)

            val settlementResponse =
                http.post("/api/v1/ledgers/${ledger.id}/settlements") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateSettlementRequest(
                            fromMembershipId = friendMembership.id,
                            toMembershipId = ledger.ownerMembershipId,
                            amount = 500,
                            currency = "NPR",
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Created, settlementResponse.status)
            val settlement: SettlementResponse = settlementResponse.body()
            assertEquals(-500L, settlement.fromBalanceBefore)
            assertEquals(500L, settlement.toBalanceBefore)
            assertEquals(0L, settlement.fromBalanceAfter)
            assertEquals(0L, settlement.toBalanceAfter)

            val after = http.get("/api/v1/ledgers/${ledger.id}/balances") { bearer(ownerId) }.body<BalancesResponse>()
            assertEquals(0L, after.balances.single { it.membershipId == ledger.ownerMembershipId }.netBalance)
            assertEquals(0L, after.balances.single { it.membershipId == friendMembership.id }.netBalance)
        }

    @Test
    fun `AC-9 a settlement between the same membership twice is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "ac9-owner@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("Solo", "NPR"))
                    }.body<LedgerResponse>()

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/settlements") {
                    bearer(ownerId)
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

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("same_membership", errorCodeOf(response.bodyAsText()))
        }

    @Test
    fun `AC-10 a non-positive settlement amount is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            val friendId = Uuid.random()
            seedUser(config, ownerId, "ac10-owner@example.com")
            seedUser(config, friendId, "ac10-friend@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("Shared", "NPR"))
                    }.body<LedgerResponse>()
            val friendMembership =
                http
                    .post("/api/v1/ledgers/${ledger.id}/members") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(AddMemberRequest("ac10-friend@example.com"))
                    }.body<MembershipResponse>()

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/settlements") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateSettlementRequest(
                            fromMembershipId = friendMembership.id,
                            toMembershipId = ledger.ownerMembershipId,
                            amount = 0,
                            currency = "NPR",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("amount_not_positive", errorCodeOf(response.bodyAsText()))
        }

    @Test
    fun `a settlement party that isn't a member of the ledger is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "outside-owner@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("Solo2", "NPR"))
                    }.body<LedgerResponse>()

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/settlements") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateSettlementRequest(
                            fromMembershipId = Uuid.random().toString(),
                            toMembershipId = ledger.ownerMembershipId,
                            amount = 100,
                            currency = "NPR",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}
