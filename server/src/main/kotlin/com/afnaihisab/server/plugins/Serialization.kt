package com.afnaihisab.server.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                // Defaults must be on the wire: clients (web now, mobile later) should never have to
                // know a Kotlin default to interpret a response.
                encodeDefaults = true
                // Tolerate fields a newer server added — forward compatibility for older clients.
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}
