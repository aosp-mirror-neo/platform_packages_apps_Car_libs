# Gemini Knowledge Update (Post-December 2024)

This document contains a high-level summary of key new features in the Android build ecosystem.
Last updated: August 2025

## Kotlin 2.2

*   **K2 Compiler is Default:** The K2 compiler is now the default and only frontend, bringing significant compilation speed improvements and more reliable type inference.
*   **JVM Default Methods:** The compiler now generates true JVM default methods for interfaces with implementations by default (`-Xjvm-default=all`). This aligns with modern Java and produces more efficient bytecode.

## Gradle (8.13+)

*   **Runtime & Dependencies:**
    *   **JDK 17+ Required (for 9.0+):** The Gradle daemon requires JDK 17 or higher.
    *   **Java 24 Support (8.14+):** Full support for using Java 24 in toolchains and for running Gradle.
    *   **Daemon JVM Auto-Provisioning (8.13+):** Gradle can automatically download the required JVM for the daemon if it's not already installed.
    *   **Kotlin 2.2 Embedded (in 9.0+):** Bundles and utilizes Kotlin 2.2 for build script execution.
    *   **`jcenter()` Removed (in 9.0+):** The `jcenter()` repository is completely removed and will fail builds.

*   **Performance:**
    *   **Configuration Cache Preferred (9.0+):** Now the officially recommended execution mode with a graceful fallback to standard execution if an incompatible task is found.
    *   **Lazy Configuration Initialization (8.14+):** Dependency configurations are now initialized lazily. Use `configurations.register()` instead of `create()` to leverage this for improved performance.
    *   **Kotlin DSL Compilation Avoidance (9.0+):** Leverages the Kotlin 2 compiler's ABI fingerprinting to avoid unnecessarily recompiling `.kts` scripts for non-code changes (e.g., comments).

*   **Build Authoring & Diagnostics:**
    *   **Reproducible Archives by Default (9.0+):** Tasks like `Jar` and `Zip` now produce byte-for-byte identical archives given the same inputs.
    *   **JSpecify Nullability Annotations (9.0+):** The Gradle API is fully annotated for nullability, enabling stricter null-safety checks in Kotlin build scripts.
    *   **Artifact Transform Report (8.13+):** A new `artifactTransforms` task helps debug dependency transformations.
    *   **Configuration Cache Integrity Check (8.14+):** A troubleshooting property (`org.gradle.configuration-cache.integrity-check`) helps diagnose serialization issues.

*   **Versioning:**
    *   **Semantic Versioning (9.0+):** Gradle now officially follows MAJOR.MINOR.PATCH semantic versioning.

## Android Gradle Plugin (AGP) 9.0

*   **Compatibility:**
    *   **Gradle 9.0 Required:** AGP 9.0 has a hard dependency on Gradle 9.0.
*   **Default Behavior Changes (Opt-In Features):**
    *   To improve out-of-the-box build performance, several features are now disabled by default and must be explicitly enabled in a module's `build.gradle.kts` if needed:
        *   `buildFeatures { aidl = true }`
        *   `buildFeatures { renderScript = true }`
        *   `buildFeatures { shaders = true }`
*   **API & Feature Removals:**
    *   **Stable APIs:** The new Variant and DSL APIs are now stable.
    *   **Wear OS App Packaging:** The `wearApp` configuration for embedding Wear OS apps in a phone app has been completely removed, aligning with modern distribution practices.
