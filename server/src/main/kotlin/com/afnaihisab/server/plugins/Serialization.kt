package com.afnaihisab.server.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * The one `Json` instance the whole server uses — for the wire format via [ContentNegotiation]
 * below, and reused by repositories that must serialize a response to store verbatim for
 * ADR-0023's idempotency replay (`docs/guidelines/exposed-koin.md`). A second, differently
 * configured `Json` would risk the stored/replayed body silently disagreeing with what
 * [ContentNegotiation] would have produced for a fresh (non-cached) response.
 */
val apiJson: Json =
    Json {
        // Defaults must be on the wire: clients (web now, mobile later) should never have to
        // know a Kotlin default to interpret a response.
        encodeDefaults = true
        // Tolerate fields a newer server added — forward compatibility for older clients.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(apiJson)
    }
}
