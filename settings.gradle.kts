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

rootProject.name = "next-gen-app"
include(":composeApp")
project(":composeApp").projectDir = file("apps/mobile-shared")
include(":androidApp")
project(":androidApp").projectDir = file("apps/android")
