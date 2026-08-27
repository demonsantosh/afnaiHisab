package com.afnaihisab.server.di

import com.afnaihisab.server.BuildInfo
import com.afnaihisab.server.config.AppConfig
import com.afnaihisab.server.health.HealthService
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module

/**
 * The `server` layer's Koin module (ADR-0005 — one module per layer). `core` gets its own module
 * when it has runtime collaborators to wire; today it is pure types with no dependencies.
 *
 * Services are declared here rather than constructed in route files so a route never reaches for a
 * concrete implementation — the seam Phase 1's repositories plug into.
 */
fun serverModule(
    config: AppConfig,
    database: Database,
) = module {
    single { config }
    single { config.database }
    single { database }
    single {
        HealthService(
            database = get(),
            serviceName = BuildInfo.SERVICE_NAME,
            version = BuildInfo.version,
        )
    }
}
