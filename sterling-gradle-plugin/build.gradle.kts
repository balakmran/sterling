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

dependencies { implementation(libs.spotless.gradle) }

// The tool versions Sterling pins, read by the plugin at runtime (ToolVersions.java).
val generatedResources = layout.buildDirectory.dir("generated/sterling-resources")
val versionResource =
    tasks.register<WriteProperties>("generateVersionResource") {
        description = "Writes Sterling's pinned tool versions into a plugin resource."
        destinationFile = generatedResources.map {
            it.file("dev/balakmran/sterling/versions.properties")
        }
        property("sterling", version)
        property("google-java-format", libs.versions.google.java.format)
        property("errorprone", libs.versions.errorprone.asProvider())
        property("nullaway", libs.versions.nullaway)
        property("jspecify", libs.versions.jspecify)
        property("spotbugs", libs.versions.spotbugs.asProvider())
        property("jacoco", libs.versions.jacoco)
    }

sourceSets.main { resources.srcDir(files(generatedResources).builtBy(versionResource)) }

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                // The Gradle API is compile-only; ProjectBuilder needs it at runtime.
                implementation(gradleApi())
                implementation(libs.assertj)
                implementation(libs.junit.jupiter.api)
            }
            // The pin test compares the version resource with these files.
            targets.configureEach {
                testTask.configure {
                    val catalog = layout.settingsDirectory.file("gradle/libs.versions.toml")
                    val versionFile = layout.settingsDirectory.file("version.txt")
                    inputs.files(catalog, versionFile).withPathSensitivity(PathSensitivity.RELATIVE)
                    systemProperty("sterling.catalog", catalog.asFile.absolutePath)
                    systemProperty("sterling.versionFile", versionFile.asFile.absolutePath)
                }
            }
        }

        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(gradleTestKit())
                implementation(libs.assertj)
                implementation(libs.junit.jupiter.api)
            }
            targets.configureEach {
                testTask.configure {
                    shouldRunAfter(tasks.test)
                    // Fixtures run builds on JDK 17 to check the below-21 failures.
                    val jdk17 = jdkPath(17)
                    jvmArgumentProviders.add(
                        CommandLineArgumentProvider { listOf("-Dsterling.jdk17=${jdk17.get()}") }
                    )
                }
            }
        }
    }
}

fun jdkPath(version: Int): Provider<String> =
    javaToolchains
        .launcherFor { languageVersion = JavaLanguageVersion.of(version) }
        .map { it.metadata.installationPath.asFile.absolutePath }

gradlePlugin {
    testSourceSets(sourceSets["functionalTest"])
    plugins {
        register("sterling") {
            id = "dev.balakmran.sterling"
            implementationClass = "dev.balakmran.sterling.SterlingPlugin"
        }
        register("sterlingJava") {
            id = "dev.balakmran.sterling.java"
            implementationClass = "dev.balakmran.sterling.SterlingJavaPlugin"
        }
    }
}

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}
