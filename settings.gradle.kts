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

include(":modules:core")
project(":modules:core").projectDir = file("modules/core")

include(":modules:design-system")
project(":modules:design-system").projectDir = file("modules/design-system")

include(":modules:data")
project(":modules:data").projectDir = file("modules/data")

include(":modules:application")
project(":modules:application").projectDir = file("modules/application")

include(":modules:features")
project(":modules:features").projectDir = file("modules/features")

include(":composeApp")
project(":composeApp").projectDir = file("apps/mobile-shared")
include(":androidApp")
project(":androidApp").projectDir = file("apps/android")
