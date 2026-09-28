import java.io.File
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}

val releaseVersion = Regex("<EvidriloReleaseVersion>\\s*([^<]+?)\\s*</EvidriloReleaseVersion>")
    .find(File(rootDir, "version.props").readText())
    ?.groupValues
    ?.get(1)
    ?.trim()
    ?: error("version.props must define EvidriloReleaseVersion")
check(releaseVersion.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
    "EvidriloReleaseVersion must use MAJOR.MINOR.PATCH format."
}

allprojects {
    group = "dev.nextgen.mobile"
    version = "$releaseVersion-SNAPSHOT"

    tasks.withType<Test>().configureEach {
        val workerTempDirectory = temporaryDir
        systemProperty("java.io.tmpdir", workerTempDirectory.absolutePath)
        doFirst {
            check(workerTempDirectory.mkdirs() || workerTempDirectory.isDirectory) {
                "Could not create Gradle test temporary directory: $workerTempDirectory"
            }
        }
    }
}
