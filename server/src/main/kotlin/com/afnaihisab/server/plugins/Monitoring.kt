package com.afnaihisab.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        // Health checks are polled constantly; logging every one buries real traffic.
        filter { call -> !call.request.path().endsWith("/health") }
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()?.value ?: "-"}"
        }
    }
}
