package com.afnaihisab.server.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Exposed table for `refresh_sessions` (`V3__add_refresh_sessions.sql`) — one row per issued
 * refresh token, the mechanism ADR-0008's rotation-on-use and family-wide reuse revocation are
 * built on. See the migration's own comment for the column-by-column rationale.
 */
@OptIn(ExperimentalUuidApi::class)
object RefreshSessionsTable : Table("refresh_sessions") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val familyId = uuid("family_id")
    val issuedAt = timestamp("issued_at")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val replacedById = uuid("replaced_by_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** A freshly issued session — the head of a new family (registration/login, AC-R1/AC-L1). */
@OptIn(ExperimentalUuidApi::class)
data class RefreshSession(
    val id: Uuid,
    val userId: Uuid,
    val familyId: Uuid,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val replacedById: Uuid? = null,
)

/** Outcome of [RefreshSessionRepository.rotate] — ADR-0008's rotation-on-use, single-use enforced. */
sealed interface RotateOutcome {
    /** The old token was valid, unused, and unexpired: a new session in the same family. */
    data class Rotated(
        val newSession: RefreshSession,
    ) : RotateOutcome

    /** No session with that id exists (never issued, or the id was forged/tampered). */
    data object NotFound : RotateOutcome

    /** The session exists but its own `expires_at` has passed. */
    data object Expired : RotateOutcome

    /**
     * The token had already been rotated once (`replaced_by_id` set) or was previously revoked —
     * reuse of a burned token, ADR-0008's theft-detection signal. Every other unrevoked session in
     * the same family has just been revoked as a side effect of this call.
     */
    data object ReuseDetected : RotateOutcome
}

/**
 * `core`-typed persistence + rotation logic for refresh sessions (ADR-0008). No HTTP route calls
 * [rotate] yet — `docs/specs/registration-login.md` only specifies register/login, not a `/refresh`
 * endpoint — but the mechanism ADR-0008 requires (single-use rotation, session-family tracking)
 * has to exist and be tested now so a future `/refresh` route is a thin route on top of this, not a
 * design exercise done under time pressure later.
 */
interface RefreshSessionRepository {
    /** Persists the head of a new session family — called once per successful register/login. */
    suspend fun issue(
        userId: Uuid,
        sessionId: Uuid,
        familyId: Uuid,
        issuedAt: Instant,
        expiresAt: Instant,
    ): RefreshSession

    /** Rotates the session identified by [sessionId], or reports why it couldn't be. */
    suspend fun rotate(
        sessionId: Uuid,
        newSessionId: Uuid,
        now: Instant,
        newExpiresAt: Instant,
    ): RotateOutcome
}

class ExposedRefreshSessionRepository(
    private val database: Database,
) : RefreshSessionRepository {
    override suspend fun issue(
        userId: Uuid,
        sessionId: Uuid,
        familyId: Uuid,
        issuedAt: Instant,
        expiresAt: Instant,
    ): RefreshSession =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                RefreshSessionsTable.insert {
                    it[id] = sessionId
                    it[this.userId] = userId
                    it[this.familyId] = familyId
                    it[this.issuedAt] = issuedAt
                    it[this.expiresAt] = expiresAt
                }
            }
            RefreshSession(sessionId, userId, familyId, issuedAt, expiresAt)
        }

    // Single-use check-and-rotate must be one atomic operation (docs/guidelines/exposed-koin.md) —
    // otherwise two concurrent uses of the same burned token could both read "not yet used" and
    // both pass, defeating the whole point of reuse detection.
    override suspend fun rotate(
        sessionId: Uuid,
        newSessionId: Uuid,
        now: Instant,
        newExpiresAt: Instant,
    ): RotateOutcome =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                val existing =
                    RefreshSessionsTable
                        .selectAll()
                        .where { RefreshSessionsTable.id eq sessionId }
                        .map(::toDomain)
                        .singleOrNull()
                        ?: return@suspendTransaction RotateOutcome.NotFound

                if (existing.revokedAt != null || existing.replacedById != null) {
                    revokeFamily(existing.familyId, now)
                    return@suspendTransaction RotateOutcome.ReuseDetected
                }

                if (existing.expiresAt < now) {
                    return@suspendTransaction RotateOutcome.Expired
                }

                RefreshSessionsTable.insert {
                    it[id] = newSessionId
                    it[userId] = existing.userId
                    it[familyId] = existing.familyId
                    it[issuedAt] = now
                    it[expiresAt] = newExpiresAt
                }
                RefreshSessionsTable.update({ RefreshSessionsTable.id eq sessionId }) {
                    it[replacedById] = newSessionId
                }

                RotateOutcome.Rotated(
                    RefreshSession(newSessionId, existing.userId, existing.familyId, now, newExpiresAt),
                )
            }
        }

    private fun revokeFamily(
        familyId: Uuid,
        now: Instant,
    ) {
        RefreshSessionsTable.update(
            { (RefreshSessionsTable.familyId eq familyId) and (RefreshSessionsTable.revokedAt.isNull()) },
        ) {
            it[revokedAt] = now
        }
    }

    private fun toDomain(row: ResultRow): RefreshSession =
        RefreshSession(
            id = row[RefreshSessionsTable.id],
            userId = row[RefreshSessionsTable.userId],
            familyId = row[RefreshSessionsTable.familyId],
            issuedAt = row[RefreshSessionsTable.issuedAt],
            expiresAt = row[RefreshSessionsTable.expiresAt],
            revokedAt = row[RefreshSessionsTable.revokedAt],
            replacedById = row[RefreshSessionsTable.replacedById],
        )
}
