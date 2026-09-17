import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.compose.compiler)
}

val localBuildProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .orElse("")
        .get()
        .reader()
        .use { load(it) }
}

fun releaseSetting(propertyName: String, environmentName: String): String =
    providers.gradleProperty(propertyName).orNull
        ?: providers.environmentVariable(environmentName).orNull
        ?: localBuildProperties.getProperty(propertyName).orEmpty()

val releaseKeystorePath = releaseSetting("androidReleaseKeystore", "EVIDRILO_ANDROID_RELEASE_KEYSTORE")
val releaseStorePassword = releaseSetting("androidReleaseStorePassword", "EVIDRILO_ANDROID_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSetting("androidReleaseKeyAlias", "EVIDRILO_ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSetting("androidReleaseKeyPassword", "EVIDRILO_ANDROID_RELEASE_KEY_PASSWORD")
val androidVersionCode = releaseSetting("androidVersionCode", "EVIDRILO_ANDROID_VERSION_CODE")
    .ifBlank { "1" }
    .toIntOrNull()
    ?: error("androidVersionCode must be a positive integer.")
val androidVersionName = releaseSetting("androidVersionName", "EVIDRILO_ANDROID_VERSION_NAME")
    .ifBlank { "0.1.0" }
    .trim()
check(androidVersionCode > 0) { "androidVersionCode must be positive." }
check(androidVersionName.matches(Regex("\\d+(?:\\.\\d+){1,2}(?:[-+][0-9A-Za-z.-]+)?"))) {
    "androidVersionName must use a release-compatible semantic version."
}
val releaseSigningValues = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val releaseSigningValueCount = releaseSigningValues.count { it.isNotBlank() }
check(releaseSigningValueCount == 0 || releaseSigningValueCount == releaseSigningValues.size) {
    "Android release signing requires all four local-only signing settings."
}
val releaseSigningConfigured = releaseSigningValueCount == releaseSigningValues.size
val releaseKeystoreFile = releaseKeystorePath.takeIf { it.isNotBlank() }?.let(::file)
if (releaseSigningConfigured) {
    check(releaseKeystoreFile?.isFile == true) {
        "The configured Android release keystore does not exist."
    }
}

android {
    namespace = "dev.nextgen.mobile.android"
    compileSdk = 35

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "dev.nextgen.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = androidVersionCode
        versionName = androidVersionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
}

tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Verifies that a non-debug Android release keystore is configured."
    doLast {
        check(releaseSigningConfigured) {
            "Android release signing is not configured. Supply the four local-only signing settings before upload."
        }
        check(releaseKeystoreFile?.isFile == true) {
            "The configured Android release keystore does not exist."
        }
    }
}
