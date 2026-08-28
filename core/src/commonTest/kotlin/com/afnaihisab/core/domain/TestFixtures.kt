package com.afnaihisab.core.domain

import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

// Deterministic fixtures shared by this package's `docs/specs/expense-split-balance.md` tests.
// Everything here is fixed/seeded — never `Uuid.random()` or a wall-clock read — per this
// project's ban on non-deterministic tests (`docs/adr/0009-testing-strategy.md`).

/** A stable, ordering-predictable id: `uuid(1) < uuid(2) < uuid(3) ...` under `Uuid`'s natural order. */
fun uuid(n: Long): Uuid = Uuid.fromLongs(0L, n)

val FIXED_CREATED_AT: Instant = Instant.parse("2026-01-15T09:00:00Z")
val FIXED_JOINED_AT: Instant = Instant.parse("2026-01-01T09:00:00Z")
val FIXED_DATE: LocalDate = LocalDate(2026, 1, 15)

fun testLedger(
    id: Uuid = uuid(0),
    currency: CurrencyCode = "USD",
): Ledger =
    Ledger(
        id = id,
        name = "Test Ledger",
        defaultCurrency = currency,
        createdAt = FIXED_CREATED_AT,
    )

fun testMembership(
    id: Uuid,
    ledgerId: Uuid,
    userId: Uuid = id,
    role: MembershipRole = MembershipRole.MEMBER,
): Membership =
    Membership(
        id = id,
        ledgerId = ledgerId,
        userId = userId,
        role = role,
        joinedAt = FIXED_JOINED_AT,
    )

/** `n` members of [ledgerId], with ids `uuid(1)..uuid(n)` — already ascending, driving AC-2's tie-break. */
fun testMembers(
    n: Int,
    ledgerId: Uuid,
): List<Membership> = (1..n).map { i -> testMembership(id = uuid(i.toLong()), ledgerId = ledgerId) }

fun testExpense(
    id: Uuid,
    ledgerId: Uuid,
    payerMembershipId: Uuid,
    amount: MinorUnits,
    currency: CurrencyCode = "USD",
): Expense =
    Expense(
        id = id,
        ledgerId = ledgerId,
        payerMembershipId = payerMembershipId,
        amount = amount,
        currency = currency,
        category = "general",
        note = null,
        date = FIXED_DATE,
        createdAt = FIXED_CREATED_AT,
        splitType = SplitType.EQUAL,
    )

fun testSplit(
    id: Uuid,
    expenseId: Uuid,
    membershipId: Uuid,
    amount: MinorUnits,
): Split = Split(id = id, expenseId = expenseId, membershipId = membershipId, amount = amount)

fun testSettlement(
    id: Uuid,
    ledgerId: Uuid,
    fromMembershipId: Uuid,
    toMembershipId: Uuid,
    amount: MinorUnits,
    currency: CurrencyCode = "USD",
): Settlement =
    Settlement(
        id = id,
        ledgerId = ledgerId,
        fromMembershipId = fromMembershipId,
        toMembershipId = toMembershipId,
        amount = amount,
        currency = currency,
        createdAt = FIXED_CREATED_AT,
    )
