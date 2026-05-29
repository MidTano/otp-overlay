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

// Single Android application module + opt-in microbenchmark module,
// both written entirely in Kotlin.
rootProject.name = "otp-overlay"
include(":app")
include(":benchmark")
