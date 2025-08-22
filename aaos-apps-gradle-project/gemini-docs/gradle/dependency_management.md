# Gradle Dependency Management

This guide outlines modern, opinionated best practices for managing dependencies and repositories in a Gradle project using the Kotlin DSL.

## 1. Centralize Repositories in `settings.gradle.kts`

The `settings.gradle.kts` file is the single source of truth for repository configuration. This improves security and consistency by preventing individual modules from adding their own repositories.

✅ **Best Practice**: Declare all repositories in `dependencyResolutionManagement` and lock down project-level repository declarations.

```kotlin
// settings.gradle.kts

dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS is the strictest and most secure option.
    // It fails the build if a repository is declared in any build.gradle.kts file.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://api.snapkit.io/maven") }
    }
}
```

### Filter Repository Content

Improve build performance and security by telling Gradle which dependencies to look for in each repository. This prevents Gradle from searching every repository for every dependency and mitigates dependency confusion attacks.

✅ **Best Practice**: Use `content` blocks to restrict repository contents.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android\\..*")
                includeGroupByRegex("androidx\\..*")
                includeGroup("com.google.android.material")
            }
        }
        mavenCentral() // General-purpose, consider adding excludes for groups in other repos.
        maven {
            url = uri("https://api.snapkit.io/maven")
            content {
                // Only look for the Snap Kit SDK here
                includeGroup("com.snap.creativekit")
            }
        }
    }
}
```

## 2. Use a Version Catalog (`libs.versions.toml`)

A Version Catalog is the standard, centralized, and type-safe way to manage dependencies, versions, and plugins across all modules.

✅ **Best Practice**: Define all dependencies in `gradle/libs.versions.toml`.

```toml
# gradle/libs.versions.toml

[versions]
agp = "9.0.0"
k8s = "2.0.0"
composeBom = "2024.08.00"
coroutines = "1.8.0"

[libraries]
# Access via `libs.compose.bom`
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
# Access via `libs.kotlinx.coroutines.core`
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }

[plugins]
# Access via `libs.plugins.android.application`
android-application = { id = "com.android.application", version.ref = "agp" }

[bundles]
# Access via `libs.bundles.compose`
compose = ["compose-ui", "compose-material3"]
```

## 3. Apply Dependencies in `build.gradle.kts`

### Use Platforms (BOMs)
A Bill of Materials (BOM) defines a set of curated, compatible library versions. This is the best way to manage versions for ecosystems like Jetpack Compose.

✅ **Best Practice**: Import BOMs using `platform()`.

```kotlin
// build.gradle.kts
dependencies {
    // Import the Compose BOM. This controls the versions of all compose libraries.
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
}
```

### Use Dependency Constraints for Transitive Versions
A constraint defines a required version for a dependency *only if* it's brought into the build transitively. It does not add the dependency itself.

✅ **Best Practice**: Use a `constraints` block to align transitive dependency versions.

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.some.library:library:1.0.0") // This transitively brings in an old coroutines version

    constraints {
        // Enforce our project's desired version of coroutines from the catalog
        implementation(libs.kotlinx.coroutines.core) {
            because("Aligning transitive versions to our project's standard.")
        }
    }
}
```

### Avoid Forcing Versions
Forcing a version is an aggressive override that can mask dependency conflicts.

⚠️ **Anti-Pattern**: Only use `resolutionStrategy.force` as a last resort to resolve a stubborn conflict after investigating the root cause. Prefer constraints.

```