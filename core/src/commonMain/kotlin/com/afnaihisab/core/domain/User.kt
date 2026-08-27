package com.afnaihisab.core.domain

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * An account holder (`docs/domain-model.md` — User).
 *
 * Invariant: [email] is unique among non-ghost users.
 *
 * @property passwordHash null for ghost users, who cannot log in until they claim the account
 *   (Phase 2 — non-app members, `docs/FEATURES.md` §b).
 * @property isGhost true for a member invited by email who has no login yet.
 */
data class User(
    val id: Uuid,
    val email: String,
    val passwordHash: String?,
    val displayName: String,
    val isGhost: Boolean = false,
    val createdAt: Instant,
)
