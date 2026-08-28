package com.afnaihisab.server.routes

import com.afnaihisab.core.data.api.ApiErrorEnvelope
import com.afnaihisab.server.module
import com.afnaihisab.server.routes.dto.AddMemberRequest
import com.afnaihisab.server.routes.dto.CreateLedgerRequest
import com.afnaihisab.server.routes.dto.LedgerResponse
import com.afnaihisab.server.routes.dto.MembershipResponse
import com.afnaihisab.server.seedUser
import com.afnaihisab.server.testAppConfig
import io.ktor.client.call.body
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
 * `POST /ledgers` (core AC-11) and `POST /ledgers/{ledgerId}/members` (core AC-12), over a real
 * routed HTTP call (`ktor-server-test-host`, ADR-0009's integration-test tier).
 */
class LedgerRoutesTest {
    @Test
    fun `creating a ledger returns 201 with the ledger and its owner membership`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "owner@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val response =
                http.post("/api/v1/ledgers") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(CreateLedgerRequest(name = "Trip to Kathmandu", defaultCurrency = "NPR"))
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body: LedgerResponse = response.body()
            assertEquals("Trip to Kathmandu", body.name)
            assertEquals("NPR", body.defaultCurrency)
        }

    @Test
    fun `creating a ledger with a blank name is rejected with 400 and the standard envelope`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "owner2@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val response =
                http.post("/api/v1/ledgers") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(CreateLedgerRequest(name = "  ", defaultCurrency = "NPR"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val envelope: ApiErrorEnvelope = response.body()
            assertEquals("VALIDATION_FAILED", envelope.error.code)
        }

    @Test
    fun `adding a member by email attaches them to the ledger`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            val memberId = Uuid.random()
            seedUser(config, ownerId, "owner3@example.com")
            seedUser(config, memberId, "friend@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest(name = "Shared flat", defaultCurrency = "NPR"))
                    }.body<LedgerResponse>()

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/members") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(AddMemberRequest(email = "friend@example.com"))
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val body: MembershipResponse = response.body()
            assertEquals(ledger.id, body.ledgerId)
            assertEquals(memberId.toString(), body.userId)
            assertEquals("MEMBER", body.role)
        }

    @Test
    fun `adding a member with an unregistered email is rejected with 400`() =
        testApplication {
            val config = testAppConfig()
            val ownerId = Uuid.random()
            seedUser(config, ownerId, "owner4@example.com")
            application { module(config) }
            val http = createClient { jsonBody() }

            val ledger =
                http
                    .post("/api/v1/ledgers") {
                        bearer(ownerId)
                        idempotencyKey()
                        contentType(ContentType.Application.Json)
                        setBody(CreateLedgerRequest(name = "Solo ledger", defaultCurrency = "NPR"))
                    }.body<LedgerResponse>()

            val response =
                http.post("/api/v1/ledgers/${ledger.id}/members") {
                    bearer(ownerId)
                    idempotencyKey()
                    contentType(ContentType.Application.Json)
                    setBody(AddMemberRequest(email = "nobody@example.com"))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}
