pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "evidrilo"

include(":modules:domain")
project(":modules:domain").projectDir = file("modules/domain")

include(":modules:design-system")
project(":modules:design-system").projectDir = file("modules/design-system")

include(":composeApp")
project(":composeApp").projectDir = file("apps/mobile-shared")
include(":androidApp")
project(":androidApp").projectDir = file("apps/android")
