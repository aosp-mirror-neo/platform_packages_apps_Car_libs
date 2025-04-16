/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

rootProject.name = "AAOS Apps"

pluginManagement {
    includeBuild("buildLogic")

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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("0.9.0")
}
apply(from = "buildLogic/findRepoRoot.settings.gradle.kts")

dependencyResolutionManagement {
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
        mavenCentral()
    }
}

/**
 * List of Unbundled projects and their corresponding relative paths. This is used to configure the
 * projects within this build.
 */
val projects =
    listOf(
        ":car-app-card-host-lib" to "../car-app-card-host-lib/app-card-host",
        ":car-app-card-host-sample-app" to "../car-app-card-host-lib/sample-host",
        ":car-app-card-lib" to "../car-app-card-lib/app-card",
        ":car-app-card-sample-calendar-app" to "../car-app-card-lib/sample-calendar-app",
        ":car-app-card-sample-media-app" to "../car-app-card-lib/sample-media-app",
        ":car-app-card-sample-weather-app" to "../car-app-card-lib/sample-weather-app",
        ":car-apps-common" to "../car-apps-common",
        ":car-assist-lib" to "../car-assist-lib",
        ":car-broadcastradio-support" to "../car-broadcastradio-support",
        ":car-media-common" to "../car-media-common",
        ":car-media-extensions" to "../car-media-extensions",
        ":car-messenger-common" to "../car-messenger-common",
        ":car-messenger-common:model" to "../car-messenger-common/model",
        ":car-rotary-lib" to "../car-ui-lib/car-rotary-lib",
        ":car-telephony-common" to "../car-telephony-common",
        ":car-testing-common" to "../car-testing-common",
        ":car-ui-lib" to "../car-ui-lib/car-ui-lib",
        ":car-ui-lib-testing" to "../car-ui-lib/car-ui-lib-testing",
        ":car-uxr-client-lib" to "../car-uxr-client-lib",
        ":oem-apis" to "../car-ui-lib/oem-apis",
        ":oem-token-lib" to "../car-ui-lib/oem-tokens/lib",
        ":oem-token-shared-lib" to "../car-ui-lib/oem-tokens/shared-lib",
        ":PaintBooth" to "../car-ui-lib/paintbooth",
        ":proxy-plugin" to "../car-ui-lib/proxy-plugin",
        ":token-compose-compat" to "../car-ui-lib/token-compose-compat",
        ":car-bugreport-app" to "../../BugReport",
        ":car-calendar-app" to "../../Calendar",
        ":car-dialer-app" to "../../Dialer",
        ":car-dialer-app:framework" to "../../Dialer/framework",
        ":car-dialer-app:testing" to "../../Dialer/testing",
        ":car-media-app" to "../../Media",
        ":car-messenger-app" to "../../Messenger",
        ":car-messenger-app:testing" to "../../Messenger/testing",
        ":car-radio-app" to "../../Radio",
        ":test-media-app" to "../../tests/TestMediaApp",
        ":test-media-app:automotive" to "../../tests/TestMediaApp/automotive",
        ":test-media-app:automotive-cal" to "../../tests/TestMediaApp/automotive-cal",
        ":test-media-app:common" to "../../tests/TestMediaApp/common",
        ":test-media-app:mobile" to "../../tests/TestMediaApp/mobile",
        ":test-rotary-ime" to "../../tests/RotaryIME",
        ":test-rotary-playground" to "../../tests/RotaryPlayground",
        ":driver-ui" to "../../DriverUI",
        ":car-dashcam-app" to "../../Dashcam/dashcam-app",
        ":car-dashcam-service" to "../../Dashcam/dashcam-service",
        ":car-dashcam-manager" to "../../Dashcam/dashcam-manager",
        ":m3u8lib" to "../../Dashcam/m3u8lib",
    )

// Initialize each Gradle subproject
projects.forEach { (projectName, projectDir) ->
    include(projectName)
    project(projectName).projectDir = File(projectDir)
}
