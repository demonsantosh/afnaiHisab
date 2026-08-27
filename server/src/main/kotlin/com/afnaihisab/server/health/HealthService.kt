package com.afnaihisab.server.health

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

/** Liveness of one dependency. */
enum class ComponentStatus {
    UP,
    DOWN,
}

/** Overall service status: [OK] only when every checked component is [ComponentStatus.UP]. */
enum class ServiceStatus {
    OK,
    DEGRADED,
}

/**
 * Wire format of `GET /api/v1/health`.
 *
 * Kept deliberately small and non-revealing — a health endpoint is unauthenticated, so it never
 * carries connection strings, stack traces, or dependency versions (ADR-0013's general posture).
 *
 * @property checkedAt ISO-8601 UTC instant, so a caller can tell a live response from a cached one.
 */
@Serializable
data class HealthResponse(
    val status: ServiceStatus,
    val service: String,
    val version: String,
    val database: ComponentStatus,
    val checkedAt: String,
)

/**
 * Builds the health report. Lives outside the route handler on purpose (ADR-0001) — the route is
 * transport only.
 */
class HealthService(
    private val database: Database,
    private val serviceName: String,
    private val version: String,
) {
    private val log = LoggerFactory.getLogger(HealthService::class.java)

    suspend fun check(): HealthResponse {
        val databaseStatus = probeDatabase()
        return HealthResponse(
            status = if (databaseStatus == ComponentStatus.UP) ServiceStatus.OK else ServiceStatus.DEGRADED,
            service = serviceName,
            version = version,
            database = databaseStatus,
            checkedAt = Instant.now().toString(),
        )
    }

    /** JDBC is blocking; keep it off Netty's event-loop threads. */
    private suspend fun probeDatabase(): ComponentStatus =
        withContext(Dispatchers.IO) {
            runCatching {
                transaction(database) { exec("select 1") }
            }.fold(
                onSuccess = { ComponentStatus.UP },
                onFailure = { error ->
                    log.warn("Database health probe failed", error)
                    ComponentStatus.DOWN
                },
            )
        }
}
