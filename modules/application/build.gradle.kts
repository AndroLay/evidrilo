import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

val localBuildProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .orElse("")
        .get()
        .reader()
        .use { load(it) }
}

fun localOrGradleProperty(name: String): String =
    providers.gradleProperty(name).orNull
        ?: localBuildProperties.getProperty(name).orEmpty()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

kotlin {
    jvmToolchain(21)
    jvm()
    androidTarget()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "EvidriloApplication"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":modules:core"))
            api(project(":modules:domain"))
            api(project(":modules:data"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        named { it.lowercase().startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "dev.nextgen.mobile.application"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "SUPABASE_URL", localOrGradleProperty("supabaseUrl").asBuildConfigString())
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            localOrGradleProperty("supabasePublishableKey").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "SUPABASE_AUTH_REDIRECT_URL",
            localOrGradleProperty("supabaseAuthRedirectUrl").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "EVIDRILO_API_BASE_URL",
            localOrGradleProperty("evidriloApiBaseUrl").asBuildConfigString(),
        )
    }

    buildFeatures {
        buildConfig = true
    }
}
