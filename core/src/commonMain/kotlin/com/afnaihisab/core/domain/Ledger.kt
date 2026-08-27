package com.afnaihisab.core.domain

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A ledger: personal (1 [Membership]) or shared (N memberships) — the same entity either way
 * (ADR-0004).
 *
 * Invariant: "personal" vs "shared" is **never** a stored field and no domain logic branches on a
 * ledger "type"; it is derived from the membership count. This is what keeps the Phase 6
 * accounting generalization additive instead of a fork.
 *
 * @property archivedAt Phase 2 (group archiving); null in Phase 1.
 */
data class Ledger(
    val id: Uuid,
    val name: String,
    val defaultCurrency: CurrencyCode,
    val createdAt: Instant,
    val archivedAt: Instant? = null,
)
