package com.afnaihisab.server

import com.afnaihisab.server.auth.testJwt
import com.afnaihisab.server.routes.dto.AddMemberRequest
import com.afnaihisab.server.routes.dto.BalancesResponse
import com.afnaihisab.server.routes.dto.CreateExpenseRequest
import com.afnaihisab.server.routes.dto.CreateLedgerRequest
import com.afnaihisab.server.routes.dto.LedgerResponse
import com.afnaihisab.server.routes.dto.MembershipResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * ADR-0009's amendment: at least one **real** HTTP round-trip (an actual TCP socket to a bound
 * port, via `ktor-client-cio`) is required before Phase 2 ships — every other test in this module
 * uses `ktor-server-test-host`'s in-process engine, which can pass even if the real wire format is
 * subtly wrong. This test boots the real `Netty` engine on an OS-assigned port and drives the full
 * create-ledger -> add-member -> create-expense -> get-balances flow through it.
 *
 * `runBlocking`, not `kotlinx-coroutines-test`'s `runTest`: this test does real socket I/O against
 * a real background Netty event loop, which virtual time has no business controlling.
 */
class ApiContractTest {
    @Test
    fun `create ledger, add member, create expense, then GET balances - over a real HTTP connection`() = runBlocking { runContractFlow() }

    private suspend fun runContractFlow() {
        val config = testAppConfig()
        val ownerId = Uuid.random()
        val friendId = Uuid.random()
        seedUser(config, ownerId, "contract-owner@example.com")
        seedUser(config, friendId, "contract-friend@example.com")

        val server = embeddedServer(Netty, port = 0, host = "127.0.0.1") { module(config) }
        server.start(wait = false)
        val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }

        try {
            val base = "http://127.0.0.1:${server.engine.resolvedConnectors().first().port}/api/v1"

            val ledger = createLedgerOverHttp(client, base, ownerId)
            val friendMembership = addMemberOverHttp(client, base, ledger, ownerId)
            createExpenseOverHttp(client, base, ledger, ownerId)
            val balances = getBalancesOverHttp(client, base, ledger, ownerId)

            assertEquals(500L, balances.balances.single { it.membershipId == ledger.ownerMembershipId }.netBalance)
            assertEquals(-500L, balances.balances.single { it.membershipId == friendMembership.id }.netBalance)
        } finally {
            client.close()
            server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 1000)
        }
    }

    private fun HttpRequestBuilder.bearer(userId: Uuid) {
        header("Authorization", "Bearer ${testJwt(userId)}")
    }

    private suspend fun createLedgerOverHttp(
        client: HttpClient,
        base: String,
        ownerId: Uuid,
    ): LedgerResponse {
        val response =
            client.post("$base/ledgers") {
                bearer(ownerId)
                header("Idempotency-Key", Uuid.random().toString())
                contentType(ContentType.Application.Json)
                setBody(CreateLedgerRequest("Real HTTP trip", "NPR"))
            }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun addMemberOverHttp(
        client: HttpClient,
        base: String,
        ledger: LedgerResponse,
        ownerId: Uuid,
    ): MembershipResponse {
        val response =
            client.post("$base/ledgers/${ledger.id}/members") {
                bearer(ownerId)
                header("Idempotency-Key", Uuid.random().toString())
                contentType(ContentType.Application.Json)
                setBody(AddMemberRequest("contract-friend@example.com"))
            }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun createExpenseOverHttp(
        client: HttpClient,
        base: String,
        ledger: LedgerResponse,
        ownerId: Uuid,
    ) {
        val response =
            client.post("$base/ledgers/${ledger.id}/expenses") {
                bearer(ownerId)
                header("Idempotency-Key", Uuid.random().toString())
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
    }

    private suspend fun getBalancesOverHttp(
        client: HttpClient,
        base: String,
        ledger: LedgerResponse,
        ownerId: Uuid,
    ): BalancesResponse {
        val response = client.get("$base/ledgers/${ledger.id}/balances") { bearer(ownerId) }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body()
    }
}
