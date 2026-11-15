pluginManagement {
    repositories {
        // @sterling-eval-repo
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}

rootProject.name = "shop"

include("orders", "billing")
