// Ktor backend (ADR-0001: `server` depends on `core`; business rules never live in route handlers).
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    application
}

// Complements ktlint (formatting) with complexity/smell/bug-pattern rules (ADR-0019 amendment).
// The plugin wires `detekt` into `check`, so `./gradlew build` and CI fail on a violation exactly
// as they already do for ktlint and the tests.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

kotlin {
    jvmToolchain(
        libs.versions.jvmTarget
            .get()
            .toInt(),
    )

    compilerOptions {
        // Matches `core` — kotlin.uuid.Uuid is the id type across the domain model.
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

application {
    mainClass.set("com.afnaihisab.server.ApplicationKt")
}

dependencies {
    implementation(project(":core"))

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)

    // DI (ADR-0005)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Persistence: Exposed over a Hikari pool; schema owned by Flyway (ADR-0019)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    runtimeOnly(libs.h2)

    // Config + logging
    implementation(libs.dotenv.kotlin)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}

// `/api/v1/health` reports the built version; substitute it here so it can never drift from
// the Gradle project version. Read via `inputs.property` + `inputs.properties[...]`, not a
// captured local — the configuration cache can serialize a task's own declared inputs, but a
// build-script-level `val` referenced from a nested execution-time lambda captures a reference to
// the (unserializable) script object itself, not just the value.
tasks.processResources {
    inputs.property("appVersion", project.version.toString())
    filesMatching("build-info.properties") {
        expand(mapOf("version" to inputs.properties["appVersion"]))
    }
}

tasks.test {
    useJUnitPlatform()
}

// `.env` (ADR-0015) and the local H2 file live at the repo root, not inside `server/`, so
// `./gradlew :server:run` must run from there — Gradle would otherwise default to `server/`.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
