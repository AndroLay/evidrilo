plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = "dev.nextgen.mobile.features"
        compileSdk = 37
        minSdk = 26
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "EvidriloFeatures"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":modules:application"))
            api(project(":modules:data"))
            api(project(":modules:domain"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
