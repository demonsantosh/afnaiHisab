// This file is named after the idempotency-key check-and-insert helper (`idempotent`) it exists
// for, not after the incidental `IdempotentResponse` data class below — same reasoning as
// `core`'s `LedgerFactory.kt` (detekt's one-class-per-matching-filename rule doesn't fit a
// function-first file).
@file:Suppress("MatchingDeclarationName")

package com.afnaihisab.server.idempotency

import com.afnaihisab.server.db.tables.IdempotencyKeysTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The exact HTTP status + JSON body a mutating endpoint sent — cached verbatim (ADR-0023). */
data class IdempotentResponse(
    val status: Int,
    val body: String,
)

/**
 * Repositories build an [IdempotentResponse] without importing `ktor-http` (persistence stays
 * free of transport types) — these are the only two statuses any repository in this project
 * produces: `201` for a successful create, `400` for a rejected `core` [ValidationResult][
 * com.afnaihisab.core.validation.ValidationResult.Invalid].
 */
const val HTTP_STATUS_CREATED: Int = 201
const val HTTP_STATUS_VALIDATION_REJECTED: Int = 400

/**
 * Runs the check-and-insert pattern ADR-0023 requires: if [key] was already processed *by
 * [userId]*, its stored [IdempotentResponse] is returned verbatim and [produce] never runs;
 * otherwise [produce] runs once and its result is stored against `(userId, key)` before being
 * returned — success or rejection alike, per ADR-0023's "process the request normally and store
 * the response".
 *
 * Scoped by `(userId, key)`, not `key` alone (kotlin-expert-review finding, 2026-08-28): an
 * unscoped key is a cross-tenant leak — a key collision or reuse across two different users would
 * otherwise return one user's cached response to the other. [userId] must be the *authenticated
 * caller's* id (never a value taken from the request body), so a colliding key value from a
 * different user can never even match this lookup.
 *
 * **Must be called from inside an already-open Exposed transaction** — this function opens none
 * itself. Every mutating repository function (`ExposedLedgerRepository.createLedgerIdempotent`,
 * etc.) calls this as the *only* thing it does inside its own single `suspendTransaction`, so the
 * idempotency-key check/insert and the write it guards are always the same atomic unit
 * (`docs/guidelines/exposed-koin.md` — "check-and-insert as one atomic transaction with the write
 * it guards"). Splitting this into a separate transaction from [produce]'s writes would let two
 * concurrent retries both pass the check — the exact race ADR-0023 exists to close.
 */
fun idempotent(
    userId: Uuid,
    key: Uuid,
    now: Instant,
    produce: () -> IdempotentResponse,
): IdempotentResponse {
    val cached =
        IdempotencyKeysTable
            .selectAll()
            .where { (IdempotencyKeysTable.userId eq userId) and (IdempotencyKeysTable.key eq key) }
            .singleOrNull()

    if (cached != null) {
        return IdempotentResponse(
            status = cached[IdempotencyKeysTable.responseStatus],
            body = cached[IdempotencyKeysTable.responseBody],
        )
    }

    val fresh = produce()
    IdempotencyKeysTable.insert {
        it[IdempotencyKeysTable.userId] = userId
        it[IdempotencyKeysTable.key] = key
        it[responseBody] = fresh.body
        it[responseStatus] = fresh.status
        it[createdAt] = now
    }
    return fresh
}
