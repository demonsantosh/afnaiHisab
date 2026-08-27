package com.afnaihisab.server.db

import com.afnaihisab.server.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Owns the connection pool, the schema, and the Exposed binding — in that order.
 *
 * Schema ownership is Flyway's, not Exposed's (ADR-0019): there is deliberately no
 * `SchemaUtils.create(...)` anywhere, so the schema a developer runs locally is byte-for-byte the
 * one CI and (later) staging apply. Exposed is the query layer only.
 */
class DatabaseFactory(
    private val config: DatabaseConfig,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    val dataSource: DataSource by lazy { createPool() }

    /**
     * Runs migrations, then binds Exposed to the pool. Returns the Exposed handle so callers pass
     * it explicitly rather than relying on Exposed's global default database.
     */
    fun connectAndMigrate(): Database {
        val ds = dataSource
        migrate(ds)
        log.info("Connecting Exposed to {}", config.url.substringBefore(';'))
        return Database.connect(ds)
    }

    private fun createPool(): HikariDataSource {
        val hikari =
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = config.maxPoolSize
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                poolName = "afnaihisab-pool"
            }
        return HikariDataSource(hikari)
    }

    private fun migrate(ds: DataSource) {
        val result =
            Flyway
                .configure()
                .dataSource(ds)
                .locations(MIGRATION_LOCATION)
                .load()
                .migrate()
        log.info("Flyway applied {} migration(s); schema at version {}", result.migrationsExecuted, result.targetSchemaVersion)
    }

    override fun close() {
        (dataSource as? HikariDataSource)?.close()
    }

    private companion object {
        const val MIGRATION_LOCATION = "classpath:db/migration"
    }
}
