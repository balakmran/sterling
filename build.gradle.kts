plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.analysis)
}

// Plugin dependencies reach consumers' classpaths, so any issue fails the build.
dependencyAnalysis {
    // TestKit test classes aren't API; keeps JUnit off functionalTestApi.
    abi { exclusions { excludeSourceSets("functionalTest") } }
    issues {
        all {
            onAny { severity("fail") }
            // The JUnit runtime is never "used"; the API isn't until a suite has tests.
            onUnusedDependencies {
                exclude("org.junit.jupiter:junit-jupiter", "org.junit.jupiter:junit-jupiter-api")
            }
        }
    }
}

tasks.named("check") { dependsOn(tasks.named("buildHealth")) }

// Sterling's own code: Java in the style Sterling enforces, build scripts with ktfmt.
spotless {
    java {
        target("sterling-gradle-plugin/src/**/*.java")
        googleJavaFormat(libs.versions.google.java.format.get())
        formatAnnotations()
    }
    kotlinGradle {
        target("*.gradle.kts", "sterling-gradle-plugin/*.gradle.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}
