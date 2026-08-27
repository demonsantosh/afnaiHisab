package com.afnaihisab.server.db

import com.afnaihisab.server.testAppConfig
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves `V1__init.sql` actually applies and produces the domain model's tables (docs/domain-model.md).
 * Without this, a syntax error in a migration would only surface the first time somebody ran the
 * server — CI would stay green on a broken schema.
 */
class MigrationTest {
    @Test
    fun `V1 creates every phase-1 table and no audit log`() {
        DatabaseFactory(testAppConfig().database).use { factory ->
            val database = factory.connectAndMigrate()

            transaction(database) {
                // Selectable => created, with the column list the code expects.
                listOf(
                    "select id, email, password_hash, display_name, is_ghost, created_at from users",
                    "select id, name, default_currency, created_at, archived_at from ledgers",
                    "select id, ledger_id, user_id, role, joined_at from memberships",
                    "select id, ledger_id, payer_membership_id, amount, currency, category, note, " +
                        "expense_date, created_at, split_type, is_locked from expenses",
                    "select id, expense_id, membership_id, amount, share_value from splits",
                    "select id, ledger_id, from_membership_id, to_membership_id, amount, currency, " +
                        "note, created_at from settlements",
                ).forEach { query ->
                    val rows = exec("$query where 1 = 0") { resultSet -> resultSet.next() }
                    assertEquals(false, rows, "unexpected rows from: $query")
                }

                // ADR-0012 puts the audit log in Phase 2 — it must not exist yet.
                val auditTableExists =
                    exec(
                        "select count(*) from information_schema.tables where lower(table_name) = 'audit_log_entries'",
                    ) { resultSet ->
                        resultSet.next()
                        resultSet.getInt(1)
                    }
                assertEquals(0, auditTableExists)
            }
        }
    }

    @Test
    fun `migrating twice is a no-op rather than an error`() {
        val config = testAppConfig().database
        DatabaseFactory(config).use { it.connectAndMigrate() }
        DatabaseFactory(config).use { factory ->
            val database = factory.connectAndMigrate()
            val version =
                transaction(database) {
                    exec("select max(version) from flyway_schema_history") { resultSet ->
                        resultSet.next()
                        resultSet.getString(1)
                    }
                }
            assertTrue(version == "1", "expected schema version 1, got $version")
        }
    }
}
