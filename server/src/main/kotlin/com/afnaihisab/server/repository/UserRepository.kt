package com.afnaihisab.server.repository

import com.afnaihisab.core.domain.User
import com.afnaihisab.server.db.tables.UsersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * Lookups + persistence against `users` (`docs/domain-model.md` — User). Also backs
 * `docs/specs/registration-login.md`'s register/login flow ([findNonGhostByEmail], [insert]), not
 * just the read-only lookups `POST /ledgers/{id}/members` needs ([findByEmail]).
 */
interface UserRepository {
    /** Any user with this email, ghost or not — login (AC-L1/AC-L2/AC-L3) needs to see both. */
    suspend fun findByEmail(email: String): User?

    /** Only a non-ghost user — registration's uniqueness check (AC-R2) only conflicts with these. */
    suspend fun findNonGhostByEmail(email: String): User?

    /**
     * Inserts [user]. Throws [org.jetbrains.exposed.v1.exceptions.ExposedSQLException] with
     * `sqlState == "23505"` on a concurrent duplicate-email race — `ux_users_email` (`V1__init.sql`)
     * is the actual, DB-enforced guarantee behind AC-R2's "creating no record," the check-then-insert
     * above it is only the fast path.
     */
    suspend fun insert(user: User): User
}

/** The only class that imports Exposed types for this repository (`docs/guidelines/exposed-koin.md`). */
class ExposedUserRepository(
    private val database: Database,
) : UserRepository {
    override suspend fun findByEmail(email: String): User? =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                UsersTable
                    .selectAll()
                    .where { UsersTable.email eq email }
                    .singleOrNull()
                    ?.toUser()
            }
        }

    override suspend fun findNonGhostByEmail(email: String): User? =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                UsersTable
                    .selectAll()
                    .where { (UsersTable.email eq email) and (UsersTable.isGhost eq false) }
                    .singleOrNull()
                    ?.toUser()
            }
        }

    override suspend fun insert(user: User): User =
        withContext(Dispatchers.IO) {
            suspendTransaction(database) {
                UsersTable.insert {
                    it[id] = user.id
                    it[email] = user.email
                    it[passwordHash] = user.passwordHash
                    it[displayName] = user.displayName
                    it[isGhost] = user.isGhost
                    it[createdAt] = user.createdAt
                }
            }
            user
        }
}

private fun ResultRow.toUser(): User =
    User(
        id = this[UsersTable.id],
        email = this[UsersTable.email],
        passwordHash = this[UsersTable.passwordHash],
        displayName = this[UsersTable.displayName],
        isGhost = this[UsersTable.isGhost],
        createdAt = this[UsersTable.createdAt],
    )
