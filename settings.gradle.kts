rootProject.name = "afnaihisab"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Phase 0 scaffolds `core` and `server` only.
// `web` is a Next.js app outside the Gradle build (ADR-0003) and `app/androidApp`
// (Phase 3) / `app/iosApp` (Phase 4) are added when those phases start (docs/PLAN.md §5).
include(":core")
include(":server")
