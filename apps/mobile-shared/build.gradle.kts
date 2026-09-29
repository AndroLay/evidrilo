import java.util.Properties
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
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

val revenueCatApiKey = localOrGradleProperty("revenuecatAndroidApiKey")
val revenueCatEntitlementId = localOrGradleProperty("revenuecatEntitlementId")
val revenueCatProductIds = localOrGradleProperty("revenuecatProductIds")
val supabaseUrl = localOrGradleProperty("supabaseUrl")
val supabasePublishableKey = localOrGradleProperty("supabasePublishableKey")
val supabaseAuthRedirectUrl = localOrGradleProperty("supabaseAuthRedirectUrl")
val evidriloApiBaseUrl = localOrGradleProperty("evidriloApiBaseUrl")
val normalizedSupabasePublishableKey = supabasePublishableKey.trim().lowercase()
val safeSupabasePublishableKey = if (
    normalizedSupabasePublishableKey.contains("service_role") ||
    normalizedSupabasePublishableKey.contains("sb_secret") ||
    normalizedSupabasePublishableKey.contains("secret")
) {
    ""
} else {
    supabasePublishableKey
}
val androidBuildConfigFields = linkedMapOf(
    "REVENUECAT_PUBLIC_SDK_KEY" to revenueCatApiKey.asKotlinStringLiteral(),
    "REVENUECAT_ENTITLEMENT_ID" to revenueCatEntitlementId.asKotlinStringLiteral(),
    "REVENUECAT_PRODUCT_IDS" to revenueCatProductIds.asKotlinStringLiteral(),
    "SUPABASE_URL" to supabaseUrl.asKotlinStringLiteral(),
    "SUPABASE_PUBLISHABLE_KEY" to safeSupabasePublishableKey.asKotlinStringLiteral(),
    "SUPABASE_AUTH_REDIRECT_URL" to supabaseAuthRedirectUrl.asKotlinStringLiteral(),
    "EVIDRILO_API_BASE_URL" to evidriloApiBaseUrl.asKotlinStringLiteral(),
    "EVIDRILO_APP_VERSION" to rootProject.version.toString().removeSuffix("-SNAPSHOT").asKotlinStringLiteral(),
)
val generatedAndroidBuildConfigDirectory = layout.buildDirectory.dir(
    "generated/androidBuildConfig/kotlin/dev/nextgen/mobile/compose",
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
        namespace = "dev.nextgen.mobile.compose"
        compileSdk = 37
        minSdk = 26
        androidResources.enable = true
        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":modules:domain"))
            api(project(":modules:core"))
            api(project(":modules:design-system"))
            api(project(":modules:data"))
            api(project(":modules:application"))
            api(project(":modules:features"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.resources)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kmp.zip.core)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.revenuecat.kmp.core)
            implementation(libs.revenuecat.kmp.ui)
        }

        val iosMain by getting {
            dependencies {
                implementation(libs.revenuecat.kmp.core)
                implementation(libs.revenuecat.kmp.ui)
            }
        }

        named { it.lowercase().startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        jvmMain.dependencies {
            implementation(libs.compose.desktop.jvm)
        }
    }

}

kotlin.sourceSets.named("androidMain") {
    kotlin.srcDir(generateAndroidBuildConfig)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.nextgen.mobile.resources"
}

compose.desktop {
    application {
        mainClass = "dev.nextgen.mobile.MainKt"
    }
}
