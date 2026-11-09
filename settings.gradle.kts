pluginManagement { repositories { gradlePluginPortal() } }

plugins {
    // Downloads JDK 21 if missing. The catalog isn't readable here, so the version is inline.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        // Spotless, Error Prone and SpotBugs Gradle plugins are published here.
        gradlePluginPortal()
    }
}

rootProject.name = "sterling"

include("sterling-gradle-plugin")
