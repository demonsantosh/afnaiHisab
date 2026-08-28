package com.afnaihisab.server.routes

import com.afnaihisab.core.domain.calculateBalances
import com.afnaihisab.server.api.pathUuidOrRespondBadRequest
import com.afnaihisab.server.api.requireLedgerMembership
import com.afnaihisab.server.repository.ExpenseRepository
import com.afnaihisab.server.repository.MembershipRepository
import com.afnaihisab.server.repository.SettlementRepository
import com.afnaihisab.server.routes.dto.BalancesResponse
import com.afnaihisab.server.routes.dto.MemberBalanceResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/** `GET /ledgers/{ledgerId}/balances` (core AC-6, AC-7) — derived on every read, never stored. */
fun Route.balanceRoutes() {
    val membershipRepository by inject<MembershipRepository>()
    val expenseRepository by inject<ExpenseRepository>()
    val settlementRepository by inject<SettlementRepository>()

    get("/ledgers/{ledgerId}/balances") {
        val ledgerId = call.pathUuidOrRespondBadRequest("ledgerId") ?: return@get
        call.requireLedgerMembership(membershipRepository, ledgerId) ?: return@get

        val members = membershipRepository.listByLedger(ledgerId)
        val (expenses, splits) = expenseRepository.allForLedger(ledgerId)
        val settlements = settlementRepository.listByLedger(ledgerId)

        val balances = calculateBalances(members, expenses, splits, settlements)
        call.respond(
            BalancesResponse(
                ledgerId = ledgerId.toString(),
                balances = balances.map { MemberBalanceResponse(it.membershipId.toString(), it.netBalance.value) },
            ),
        )
    }
}
