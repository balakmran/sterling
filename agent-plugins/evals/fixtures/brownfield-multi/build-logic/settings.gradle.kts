dependencyResolutionManagement {
    repositories {
        // @sterling-eval-repo
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}

rootProject.name = "build-logic"
