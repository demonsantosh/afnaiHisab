package com.afnaihisab.server

import com.afnaihisab.server.config.AppConfig
import com.afnaihisab.server.db.DatabaseFactory
import com.afnaihisab.server.db.tables.UsersTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Seeds a `users` row directly against the database [config] points at. Registration/login are not
 * built yet (out of scope for `docs/specs/expense-split-balance-api.md`), so every route test needs
 * a way to make a user "exist" without a real signup flow — this is that stand-in, exactly as the
 * task's test-JWT helper (`auth/TestJwt.kt`) stands in for logging in.
 *
 * Call this *before* `application { module(config) }` inside a `testApplication { }` block.
 * Deliberately never closes the [DatabaseFactory]/pool it opens (a harmless leak for a single short
 * test): H2's `DB_CLOSE_DELAY=-1` (`testAppConfig`) is meant to keep an in-memory database alive
 * with zero open connections, but keeping this seeding pool's connection alive for the test's
 * duration sidesteps relying on that entirely — the server's own pool (opened later inside
 * `module(config)`) then always finds an already-migrated, already-seeded, definitely-still-alive
 * database rather than racing a pool-teardown/H2-auto-close window.
 */
fun seedUser(
    config: AppConfig,
    userId: Uuid,
    email: String,
    displayName: String = "Test User",
) {
    val database = DatabaseFactory(config.database).connectAndMigrate()
    transaction(database) {
        UsersTable.insert {
            it[id] = userId
            it[UsersTable.email] = email
            it[passwordHash] = null
            it[UsersTable.displayName] = displayName
            it[isGhost] = false
            it[createdAt] = Clock.System.now()
        }
    }
}
