import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
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

kotlin {
    jvmToolchain(21)
    jvm()
    androidTarget()

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
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
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

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }

}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.nextgen.mobile.resources"
}

android {
    namespace = "dev.nextgen.mobile.compose"
    compileSdk = 35

    val revenueCatApiKey = localOrGradleProperty("revenuecatAndroidApiKey")
    val revenueCatEntitlementId = localOrGradleProperty("revenuecatEntitlementId")
    val revenueCatProductIds = localOrGradleProperty("revenuecatProductIds")
    val supabaseUrl = localOrGradleProperty("supabaseUrl")
    val supabasePublishableKey = localOrGradleProperty("supabasePublishableKey")
    val supabaseAuthRedirectUrl = localOrGradleProperty("supabaseAuthRedirectUrl")
    val evidriloApiBaseUrl = localOrGradleProperty("evidriloApiBaseUrl")

    fun String.asBuildConfigString(): String =
        "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    fun String.asSafeClientConfigString(): String {
        val normalized = trim().lowercase()
        return if (normalized.contains("service_role") || normalized.contains("sb_secret") || normalized.contains("secret")) {
            "".asBuildConfigString()
        } else {
            asBuildConfigString()
        }
    }

    defaultConfig {
        minSdk = 26
        buildConfigField(
            "String",
            "REVENUECAT_PUBLIC_SDK_KEY",
            revenueCatApiKey.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "REVENUECAT_ENTITLEMENT_ID",
            revenueCatEntitlementId.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "REVENUECAT_PRODUCT_IDS",
            revenueCatProductIds.asBuildConfigString(),
        )
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", supabasePublishableKey.asSafeClientConfigString())
        buildConfigField("String", "SUPABASE_AUTH_REDIRECT_URL", supabaseAuthRedirectUrl.asBuildConfigString())
        buildConfigField("String", "EVIDRILO_API_BASE_URL", evidriloApiBaseUrl.asBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
    }
}

compose.desktop {
    application {
        mainClass = "dev.nextgen.mobile.MainKt"
    }
}
