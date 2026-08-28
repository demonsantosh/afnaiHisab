package com.afnaihisab.server.routes.dto

import kotlinx.serialization.Serializable

/**
 * Wire-format request/response shapes for `docs/specs/expense-split-balance-api.md`'s six
 * endpoints. These are `server`-only types (never imported by `core`) — the boundary between
 * `core`'s domain types (`Uuid`, `MinorUnits`, `CurrencyCode`, `Instant`) and JSON, exactly like
 * `docs/guidelines/exposed-koin.md`'s Exposed boundary unwraps the same value classes for SQL.
 */
@Serializable
data class CreateLedgerRequest(
    val name: String,
    val defaultCurrency: String,
)

@Serializable
data class LedgerResponse(
    val id: String,
    val name: String,
    val defaultCurrency: String,
    val createdAt: String,
    val ownerMembershipId: String,
)

/** Adds a member by email (`core`'s `addMember` KDoc — email resolution is a `server` concern). */
@Serializable
data class AddMemberRequest(
    val email: String,
)

@Serializable
data class MembershipResponse(
    val id: String,
    val ledgerId: String,
    val userId: String,
    val role: String,
    val joinedAt: String,
)

@Serializable
data class CreateExpenseRequest(
    val payerMembershipId: String,
    val amount: Long,
    val currency: String,
    val category: String,
    val note: String? = null,
    /** ISO-8601, e.g. `"2026-08-28"`. */
    val date: String,
)

@Serializable
data class SplitResponse(
    val id: String,
    val membershipId: String,
    val amount: Long,
)

@Serializable
data class ExpenseResponse(
    val id: String,
    val ledgerId: String,
    val payerMembershipId: String,
    val amount: Long,
    val currency: String,
    val category: String,
    val note: String? = null,
    val date: String,
    val createdAt: String,
    val splitType: String,
    val splits: List<SplitResponse>,
)

@Serializable
data class MemberBalanceResponse(
    val membershipId: String,
    val netBalance: Long,
)

@Serializable
data class BalancesResponse(
    val ledgerId: String,
    val balances: List<MemberBalanceResponse>,
)

@Serializable
data class CreateSettlementRequest(
    val fromMembershipId: String,
    val toMembershipId: String,
    val amount: Long,
    val currency: String,
    val note: String? = null,
)

/** AC-13 — every settlement reports both parties' balance immediately before and after it. */
@Serializable
data class SettlementResponse(
    val id: String,
    val ledgerId: String,
    val fromMembershipId: String,
    val toMembershipId: String,
    val amount: Long,
    val currency: String,
    val note: String? = null,
    val createdAt: String,
    val fromBalanceBefore: Long,
    val toBalanceBefore: Long,
    val fromBalanceAfter: Long,
    val toBalanceAfter: Long,
)
