package com.afnaihisab.server

import java.util.Properties

/**
 * Build identity, filled in from Gradle at `processResources` time so the version reported by
 * `/api/v1/health` cannot drift from the version actually built.
 */
object BuildInfo {
    const val SERVICE_NAME: String = "afnaihisab-server"

    val version: String by lazy {
        val properties = Properties()
        BuildInfo::class.java.getResourceAsStream("/build-info.properties")?.use(properties::load)
        properties.getProperty("version")?.takeIf { it.isNotBlank() } ?: "unknown"
    }
}
