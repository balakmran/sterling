# Multi-module builds

Sterling goes in once, in a convention plugin, so every module gets the same
version and settings. Detection's `buildLogic` says whether the build has
`build-logic` or `buildSrc`.

## A convention plugin exists

1. In its `build.gradle.kts`, replace the Spotless and SpotBugs plugin
   dependencies with Sterling's:

   ```kotlin
   dependencies {
       implementation("dev.balakmran.sterling:sterling-gradle-plugin:<version>")
   }
   ```

   Keep the Spotless dependency only if the convention plugin still applies
   `com.diffplug.spotless` for steps of its own. If the version comes from the
   version catalog, add the library there and use it:

   ```toml
   [versions]
   sterling = "<version>"

   [libraries]
   sterling-gradle-plugin = { module = "dev.balakmran.sterling:sterling-gradle-plugin", version.ref = "sterling" }
   ```

   then `implementation(libs.sterling.gradle.plugin)`.
2. In the convention plugin, apply `id("dev.balakmran.sterling")` last in
   `plugins { }`. The modules that apply the convention plugin get Sterling.
3. Shared settings (`format.style`, `errorProne { disable(...) }`) go in the
   convention plugin's `sterling { }` block.

## No convention plugin

Declare Sterling once in the root build and apply it in each module with Java
code, so the version lives in one place:

```kotlin
// root build.gradle.kts
plugins { id("dev.balakmran.sterling") version "<version>" apply false }

// each module's build.gradle.kts
plugins { id("dev.balakmran.sterling") }
```

If the root build has no plugin block, create one. Don't add a convention
plugin: that is a larger change than adopting Sterling, and the user didn't
ask for it.

## Per module

- `sterling.profile` is a project property in `gradle.properties`, so it covers
  every module.
- Coverage is measured and checked per module. Each module's thresholds go in
  that module's own `build.gradle.kts`, never in the convention plugin.
- `./gradlew stlCheck` at the root runs every module's gate. The `-q stlInfo
  --json` output has one line per module.
