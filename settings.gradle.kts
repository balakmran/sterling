pluginManagement { repositories { gradlePluginPortal() } }

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
