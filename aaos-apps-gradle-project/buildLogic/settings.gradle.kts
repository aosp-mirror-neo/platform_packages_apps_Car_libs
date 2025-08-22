/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("UnstableApiUsage")

rootProject.name = "buildLogic"

pluginManagement {
    repositories {
        // Only check the google repository for these groups
        // This makes dependency resolution much faster by telling Gradle that it'll only find
        // Google libraries and plugins within the gmaven repository.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Need to manually load the libs.versions.toml, since it's in the root project
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
    // Fail the build if any project tries to declare it's own repositories
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0") }

// Finds the root of the repository and makes it available for projects to use
apply(from = "findRepoRoot.settings.gradle.kts")

include("projectPlugin")
