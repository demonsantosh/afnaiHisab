plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Complements ktlint (formatting) with complexity/smell/bug-pattern rules (ADR-0019 amendment).
// The plugin wires `detekt` into `check`, so `./gradlew build` and CI fail on a violation exactly
// as they already do for ktlint and the tests.
//
// `source` is set explicitly: detekt's default source dirs are the JVM-project `src/{main,test}`
// layout, which matches none of this multiplatform module's source sets.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
    )
}

kotlin {
    jvmToolchain(
        libs.versions.jvmTarget
            .get()
            .toInt(),
    )

    // Phase 0 declares the JVM target only — it is the single consumer (`server`) today.
    // androidTarget() and the iOS targets get added in Phase 3/4 (docs/PLAN.md §5); all
    // code below already lives in commonMain so adding them is additive, not a rewrite.
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    compilerOptions {
        // kotlin.uuid.Uuid is the multiplatform UUID type used for every entity id
        // (docs/domain-model.md "ID strategy").
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}
