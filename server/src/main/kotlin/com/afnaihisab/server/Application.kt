package com.afnaihisab.server

import com.afnaihisab.server.config.AppConfig
import com.afnaihisab.server.db.DatabaseFactory
import com.afnaihisab.server.di.serverModule
import com.afnaihisab.server.plugins.configureCors
import com.afnaihisab.server.plugins.configureErrorHandling
import com.afnaihisab.server.plugins.configureMonitoring
import com.afnaihisab.server.plugins.configureSerialization
import com.afnaihisab.server.routes.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    val config = AppConfig.load()
    embeddedServer(
        factory = Netty,
        port = config.port,
        host = config.host,
    ) { module(config) }.start(wait = true)
}

/**
 * The whole application graph, as one function, so `ktor-server-test-host` boots exactly what
 * production boots (ADR-0009) — a test that passes against a hand-assembled subset proves nothing.
 *
 * @param config defaults to environment + `.env` (ADR-0015); tests pass an in-memory-database
 *   config instead.
 */
fun Application.module(config: AppConfig = AppConfig.load()) {
    val databaseFactory = DatabaseFactory(config.database)
    val database = databaseFactory.connectAndMigrate()

    install(Koin) {
        slf4jLogger()
        modules(serverModule(config, database))
    }

    configureSerialization()
    configureMonitoring()
    // Error handling is installed before routing so a failure anywhere below it still comes back
    // in ADR-0015's envelope.
    configureErrorHandling()
    configureCors(config)
    configureRouting()

    monitor.subscribe(ApplicationStopped) {
        databaseFactory.close()
    }

    log.info(
        "{} {} ready on http://{}:{}{} (env: {})",
        BuildInfo.SERVICE_NAME,
        BuildInfo.version,
        config.host,
        config.port,
        "/api/v1",
        config.environment,
    )
}
