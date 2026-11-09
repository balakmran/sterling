plugins {
    `java-gradle-plugin`
    // Version comes from the root build, which aggregates buildHealth.
    id("com.autonomousapps.dependency-analysis")
}

group = "dev.balakmran.sterling"

version = providers.fileContents(layout.settingsDirectory.file("version.txt")).asText.get().trim()

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

tasks.withType<JavaCompile>().configureEach {
    // JVM 17 bytecode loads on a JDK 17 daemon, so Sterling can fail with a clear message.
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
            dependencies { implementation(libs.junit.jupiter.api) }
        }

        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(gradleTestKit())
                implementation(libs.junit.jupiter.api)
            }
            targets.configureEach { testTask.configure { shouldRunAfter(tasks.test) } }
        }
    }
}

gradlePlugin { testSourceSets(sourceSets["functionalTest"]) }

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}
