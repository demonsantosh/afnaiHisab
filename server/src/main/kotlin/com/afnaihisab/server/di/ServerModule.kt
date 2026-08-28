package com.afnaihisab.server.di

import com.afnaihisab.server.BuildInfo
import com.afnaihisab.server.auth.AuthService
import com.afnaihisab.server.auth.ExposedRefreshSessionRepository
import com.afnaihisab.server.auth.JwtService
import com.afnaihisab.server.auth.PasswordHasher
import com.afnaihisab.server.auth.RefreshSessionRepository
import com.afnaihisab.server.config.AppConfig
import com.afnaihisab.server.health.HealthService
import com.afnaihisab.server.plugins.apiJson
import com.afnaihisab.server.repository.ExpenseRepository
import com.afnaihisab.server.repository.ExposedExpenseRepository
import com.afnaihisab.server.repository.ExposedLedgerRepository
import com.afnaihisab.server.repository.ExposedMembershipRepository
import com.afnaihisab.server.repository.ExposedSettlementRepository
import com.afnaihisab.server.repository.ExposedUserRepository
import com.afnaihisab.server.repository.LedgerRepository
import com.afnaihisab.server.repository.MembershipRepository
import com.afnaihisab.server.repository.SettlementRepository
import com.afnaihisab.server.repository.UserRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.bind
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
    single { apiJson }
    single {
        HealthService(
            database = get(),
            serviceName = BuildInfo.SERVICE_NAME,
            version = BuildInfo.version,
        )
    }

    // Repositories (docs/guidelines/exposed-koin.md — `single`, since each wraps the same
    // effectively-singleton Hikari-backed `Database`).
    single { ExposedUserRepository(get()) } bind UserRepository::class
    single { ExposedLedgerRepository(get(), get()) } bind LedgerRepository::class
    single { ExposedMembershipRepository(get(), get()) } bind MembershipRepository::class
    single { ExposedExpenseRepository(get(), get()) } bind ExpenseRepository::class
    single { ExposedSettlementRepository(get(), get()) } bind SettlementRepository::class

    // Auth (ADR-0008, ADR-0030) — `single` scope for the repository per
    // docs/guidelines/exposed-koin.md (shares the already-singleton-lifetime connection pool).
    single { ExposedRefreshSessionRepository(database = get()) } bind RefreshSessionRepository::class
    single { PasswordHasher() }
    single { JwtService(config = get<AppConfig>().jwt) }
    single {
        AuthService(
            userRepository = get(),
            refreshSessionRepository = get(),
            passwordHasher = get(),
            jwtService = get(),
        )
    }
}
