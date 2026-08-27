// Root build file. Plugins are declared (not applied) here so every module resolves
// the same pinned versions from gradle/libs.versions.toml (ADR-0017: no floating versions).
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "com.afnaihisab"
    version = "0.1.0"
}
