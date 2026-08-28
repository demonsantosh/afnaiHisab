package com.afnaihisab.server.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

/** Column length constants, named after `V1__init.sql`'s declared `varchar(n)` lengths. */
private const val EMAIL_MAX_LENGTH = 320
private const val PASSWORD_HASH_MAX_LENGTH = 255
private const val NAME_MAX_LENGTH = 120
private const val CURRENCY_CODE_LENGTH = 3
private const val ROLE_MAX_LENGTH = 16
private const val CATEGORY_MAX_LENGTH = 64
private const val NOTE_MAX_LENGTH = 1000
private const val SPLIT_TYPE_MAX_LENGTH = 16

/**
 * Exposed DSL table objects (`docs/guidelines/exposed-koin.md` — DSL, not DAO). Column
 * names/types/constraints mirror `V1__init.sql`/`V2__add_missing_fk_indexes.sql`/
 * `V3__add_idempotency_keys.sql` exactly; Flyway owns the schema (ADR-0019), these are the query
 * layer only — nothing here ever calls `SchemaUtils.create`.
 *
 * Every repository unwraps/wraps `core`'s value classes (`MinorUnits`, `CurrencyCode`) at this
 * boundary; these `Table` objects only ever see the raw `Long`/`String` primitives.
 */
object UsersTable : Table("users") {
    val id = uuid("id")
    val email = varchar("email", EMAIL_MAX_LENGTH)
    val passwordHash = varchar("password_hash", PASSWORD_HASH_MAX_LENGTH).nullable()
    val displayName = varchar("display_name", NAME_MAX_LENGTH)
    val isGhost = bool("is_ghost")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object LedgersTable : Table("ledgers") {
    val id = uuid("id")
    val name = varchar("name", NAME_MAX_LENGTH)
    val defaultCurrency = varchar("default_currency", CURRENCY_CODE_LENGTH)
    val createdAt = timestamp("created_at")
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object MembershipsTable : Table("memberships") {
    val id = uuid("id")
    val ledgerId = uuid("ledger_id").references(LedgersTable.id)
    val userId = uuid("user_id").references(UsersTable.id)
    val role = varchar("role", ROLE_MAX_LENGTH)
    val joinedAt = timestamp("joined_at")

    override val primaryKey = PrimaryKey(id)
}

object ExpensesTable : Table("expenses") {
    val id = uuid("id")
    val ledgerId = uuid("ledger_id").references(LedgersTable.id)
    val payerMembershipId = uuid("payer_membership_id").references(MembershipsTable.id)
    val amount = long("amount")
    val currency = varchar("currency", CURRENCY_CODE_LENGTH)
    val category = varchar("category", CATEGORY_MAX_LENGTH)
    val note = varchar("note", NOTE_MAX_LENGTH).nullable()
    val expenseDate = date("expense_date")
    val createdAt = timestamp("created_at")
    val splitType = varchar("split_type", SPLIT_TYPE_MAX_LENGTH)
    val isLocked = bool("is_locked")

    override val primaryKey = PrimaryKey(id)
}

object SplitsTable : Table("splits") {
    val id = uuid("id")
    val expenseId = uuid("expense_id").references(ExpensesTable.id)
    val membershipId = uuid("membership_id").references(MembershipsTable.id)
    val amount = long("amount")
    val shareValue = long("share_value").nullable()

    override val primaryKey = PrimaryKey(id)
}

object SettlementsTable : Table("settlements") {
    val id = uuid("id")
    val ledgerId = uuid("ledger_id").references(LedgersTable.id)
    val fromMembershipId = uuid("from_membership_id").references(MembershipsTable.id)
    val toMembershipId = uuid("to_membership_id").references(MembershipsTable.id)
    val amount = long("amount")
    val currency = varchar("currency", CURRENCY_CODE_LENGTH)
    val note = varchar("note", NOTE_MAX_LENGTH).nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

/**
 * ADR-0023 / `docs/domain-model.md` "IdempotencyKey". The column is `idempotency_key`, not the
 * bare `key` `docs/domain-model.md` sketches — `key` collides with the reserved SQL keyword
 * (`V3__add_idempotency_keys.sql` explains why).
 *
 * Scoped by (`userId`, `key`), not `key` alone (kotlin-expert-review finding, 2026-08-28): an
 * unscoped key is a cross-tenant leak — see the migration's comment for the full reasoning.
 */
object IdempotencyKeysTable : Table("idempotency_keys") {
    val userId = uuid("user_id")
    val key = uuid("idempotency_key")
    val responseBody = text("response_body")
    val responseStatus = integer("response_status")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(userId, key)
}
