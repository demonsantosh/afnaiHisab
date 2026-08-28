package com.afnaihisab.server.routes

import com.afnaihisab.server.module
import com.afnaihisab.server.routes.dto.AddMemberRequest
import com.afnaihisab.server.routes.dto.CreateExpenseRequest
import com.afnaihisab.server.routes.dto.CreateLedgerRequest
import com.afnaihisab.server.routes.dto.CreateSettlementRequest
import com.afnaihisab.server.routes.dto.ExpenseResponse
import com.afnaihisab.server.routes.dto.LedgerResponse
import com.afnaihisab.server.routes.dto.MembershipResponse
import com.afnaihisab.server.seedUser
import com.afnaihisab.server.testAppConfig
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

/**
 * ADR-0023: AC-S2 (a repeated `Idempotency-Key` returns the original response, no second record)
 * and AC-S3 (a missing key is rejected `400`), on every `POST` this spec adds.
 */
class IdempotencyTest {
    @Test
    fun `AC-S2 a repeated idempotency key on expense creation returns the cached response, not a second expense`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "idem-owner@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("Trip", "NPR"))
                    }.body<LedgerResponse>()

            val key = Uuid.random()
            val request =
                CreateExpenseRequest(
                    payerMembershipId = ledger.ownerMembershipId,
                    amount = 1000,
                    currency = "NPR",
                    category = "food",
                    date = "2026-08-28",
                )

            val first =
                http.post("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(ownerId)
                    header("Idempotency-Key", key.toString())
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            val second =
                http.post("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(ownerId)
                    header("Idempotency-Key", key.toString())
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Created, first.status)
            assertEquals(HttpStatusCode.Created, second.status)
            assertEquals(first.bodyAsText(), second.bodyAsText())
            val firstBody: ExpenseResponse = Json.decodeFromString(first.bodyAsText())
            val secondBody: ExpenseResponse = Json.decodeFromString(second.bodyAsText())
            assertEquals(firstBody.id, secondBody.id)

            val list =
                http.get("/api/v1/ledgers/${ledger.id}/expenses") { bearer(ownerId) }.bodyAsText()
            assertEquals(
                1,
                Json
                    .parseToJsonElement(list)
                    .jsonObject["items"]!!
                    .jsonArray.size,
            )
        }

    @Test
    fun `two different users reusing the same idempotency key value each get their own ledger, not a collision`() =
        testApplication {
            val config = testAppConfig()
            val userA = Uuid.random()
            val userB = Uuid.random()
            seedUser(config, userA, "idem-scope-a@example.com")
            seedUser(config, userB, "idem-scope-b@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            // A colliding/reused key value across two different users must never leak one user's
            // cached response to the other (kotlin-expert-review finding, 2026-08-28) — the
            // idempotency table is scoped by (userId, key), not key alone.
            val sharedKey = Uuid.random()

            val ledgerForA =
                http
                    .post("/api/v1/ledgers") {
                        bearer(userA)
                        header("Idempotency-Key", sharedKey.toString())
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("User A's ledger", "NPR"))
                    }.body<LedgerResponse>()

            val ledgerForB =
                http
                    .post("/api/v1/ledgers") {
                        bearer(userB)
                        header("Idempotency-Key", sharedKey.toString())
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("User B's ledger", "USD"))
                    }.body<LedgerResponse>()

            assertEquals("User A's ledger", ledgerForA.name)
            assertEquals("User B's ledger", ledgerForB.name)
            assertNotEquals(ledgerForA.id, ledgerForB.id, "expected two distinct ledgers, not a cross-user cache hit")
        }

    @Test
    fun `AC-S2 a repeated idempotency key on settlement creation never records a second settlement`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            val friendId = Uuid.random()
            seedUser(config, ownerId, "idem-settle-owner@example.com")
            seedUser(config, friendId, "idem-settle-friend@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("Trip", "NPR"))
                    }.body<LedgerResponse>()
            val friendMembership =
                http
                    .post("/api/v1/ledgers/${ledger.id}/members") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(AddMemberRequest("idem-settle-friend@example.com"))
                    }.body<MembershipResponse>()

            val key = Uuid.random()
            val request =
                CreateSettlementRequest(
                    fromMembershipId = friendMembership.id,
                    toMembershipId = ledger.ownerMembershipId,
                    amount = 100,
                    currency = "NPR",
                )

            val first =
                http.post("/api/v1/ledgers/${ledger.id}/settlements") {
                    bearer(ownerId)
                    header("Idempotency-Key", key.toString())
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            val second =
                http.post("/api/v1/ledgers/${ledger.id}/settlements") {
                    bearer(ownerId)
                    header("Idempotency-Key", key.toString())
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

            assertEquals(HttpStatusCode.Created, first.status)
            assertEquals(first.bodyAsText(), second.bodyAsText())

            // If a second settlement had actually been recorded, the owner's balance would be
            // -200 (two -100 receipts), not -100 — this is the AC-6/AC-7 assertion that proves
            // idempotency, not just "the same bytes came back." (Owner *received* 100 with no
            // offsetting expense, so netBalance is -100, per calculateBalances's formula.)
            val balances =
                http
                    .get("/api/v1/ledgers/${ledger.id}/balances") { bearer(ownerId) }
                    .body<com.afnaihisab.server.routes.dto.BalancesResponse>()
            assertEquals(-100L, balances.balances.single { it.membershipId == ledger.ownerMembershipId }.netBalance)
        }

    @Test
    fun `AC-S3 creating a ledger without an Idempotency-Key header is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "no-key-ledger@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val response =
                http.post("/api/v1/ledgers") {
                    bearer(ownerId)
                    contentType(ContentType.Application.Json)
                    setBody(CreateLedgerRequest("Trip", "NPR"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `AC-S3 adding a member without an Idempotency-Key header is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "no-key-member@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("Trip", "NPR"))
                    }.body<LedgerResponse>()

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/members") {
                    bearer(ownerId)
                    contentType(ContentType.Application.Json)
                    setBody(AddMemberRequest("nobody@example.com"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `AC-S3 creating an expense without an Idempotency-Key header is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "no-key-expense@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("Trip", "NPR"))
                    }.body<LedgerResponse>()

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/expenses") {
                    bearer(ownerId)
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

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `AC-S3 creating a settlement without an Idempotency-Key header is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "no-key-settlement@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }
            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest("Trip", "NPR"))
                    }.body<LedgerResponse>()

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/settlements") {
                    bearer(ownerId)
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
        }
}
