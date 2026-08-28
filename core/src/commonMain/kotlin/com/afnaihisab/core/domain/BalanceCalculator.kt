package com.afnaihisab.core.domain

/**
 * Derives each of [members]'s net balance for a ledger from its [Expense]s, [Split]s and
 * [Settlement]s — **never stored** (`docs/specs/expense-split-balance.md` AC-6, AC-7;
 * `docs/domain-model.md` "Derived (never stored)").
 *
 * This is a human-review lane (ADR-0017) — an error here is silently wrong financial data.
 *
 * Behavior — for each membership `m` in [members]:
 * ```
 * netBalance(m) = (sum of Expense.amount for expenses in `expenses` where payerMembershipId == m.id)
 *               - (sum of Split.amount for splits in `splits` where membershipId == m.id, across
 *                  ALL expenses, including ones m paid for themself)
 *               - (sum of Settlement.amount for settlements in `settlements` where toMembershipId == m.id)
 *               + (sum of Settlement.amount for settlements in `settlements` where fromMembershipId == m.id)
 * ```
 * Positive = this member is owed money; negative = this member owes money. This is a precise,
 * non-double-counting restatement of AC-6's prose ("sum of Split amounts where they are owed ...
 * minus sum of Split amounts they owe ... minus net Settlements"): a member's own split, even on
 * an expense they paid for themself, is subtracted exactly once, via the second term.
 *
 * AC-7: when [splits] and [settlements] fully settle every [expenses] entry, every returned
 * `netBalance` is exactly `0` — integer minor-unit arithmetic throughout means there is no
 * floating-point residue to reason about (`docs/domain-model.md`'s money invariant).
 *
 * @param members the ledger's current members; the result has exactly one [MemberBalance] per
 *   entry, **in the same order** as [members] (not sorted or filtered).
 * @param expenses the ledger's expenses — only [Expense.payerMembershipId] and [Expense.amount]
 *   matter to this computation.
 * @param splits every [Split] belonging to an expense in [expenses] (from any member, not just
 *   `members` — callers are expected to pass a consistent, already-filtered-to-this-ledger set).
 * @param settlements every [Settlement] recorded against this ledger.
 */
fun calculateBalances(
    members: List<Membership>,
    expenses: List<Expense>,
    splits: List<Split>,
    settlements: List<Settlement>,
): List<MemberBalance> =
    members.map { member ->
        val paid = expenses.filter { it.payerMembershipId == member.id }.sumOf { it.amount }
        val owed = splits.filter { it.membershipId == member.id }.sumOf { it.amount }
        val received = settlements.filter { it.toMembershipId == member.id }.sumOf { it.amount }
        val paidOut = settlements.filter { it.fromMembershipId == member.id }.sumOf { it.amount }
        MemberBalance(membershipId = member.id, netBalance = paid - owed - received + paidOut)
    }
