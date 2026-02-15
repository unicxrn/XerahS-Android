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

rootProject.name = "XerahS"

include(":app")
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":feature:capture")
include(":feature:annotation")
include(":feature:upload")
include(":feature:history")
include(":feature:settings")
