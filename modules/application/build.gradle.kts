import java.util.Properties
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
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

fun String.asKotlinStringLiteral(): String {
    val dollar = 36.toChar().toString()
    val escaped = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace(dollar, "\\" + dollar)
    return "\"" + escaped + "\""
}

fun String.asBuildConfigBoolean(): String = when (trim().lowercase()) {
    "true" -> "true"
    "false" -> "false"
    else -> error("Auth provider build flags must be true or false.")
}

val androidBuildConfigFields = linkedMapOf(
    "SUPABASE_URL" to localOrGradleProperty("supabaseUrl").asKotlinStringLiteral(),
    "SUPABASE_PUBLISHABLE_KEY" to localOrGradleProperty("supabasePublishableKey").asKotlinStringLiteral(),
    "SUPABASE_AUTH_REDIRECT_URL" to localOrGradleProperty("supabaseAuthRedirectUrl").asKotlinStringLiteral(),
    "EVIDRILO_API_BASE_URL" to localOrGradleProperty("evidriloApiBaseUrl").asKotlinStringLiteral(),
    "SUPABASE_GOOGLE_AUTH_ENABLED" to localOrGradleProperty("supabaseGoogleAuthEnabled")
        .ifBlank { "false" }
        .asBuildConfigBoolean(),
    "SUPABASE_APPLE_AUTH_ENABLED" to localOrGradleProperty("supabaseAppleAuthEnabled")
        .ifBlank { "false" }
        .asBuildConfigBoolean(),
)
val generatedAndroidBuildConfigDirectory = layout.buildDirectory.dir(
    "generated/androidBuildConfig/kotlin/dev/nextgen/mobile/application",
)
val generateAndroidBuildConfig = tasks.register<Copy>("generateEvidriloAndroidBuildConfig") {
    inputs.properties(androidBuildConfigFields)
    from(layout.projectDirectory.file("build-support/BuildConfig.kt.template"))
    into(generatedAndroidBuildConfigDirectory)
    rename { "BuildConfig.kt" }
    expand(androidBuildConfigFields)
}

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = "dev.nextgen.mobile.application"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

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
            implementation(libs.kmp.zip.core)
        }

        named { it.lowercase().startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }

        named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

kotlin.sourceSets.named("androidMain") {
    kotlin.srcDir(generateAndroidBuildConfig)
}
