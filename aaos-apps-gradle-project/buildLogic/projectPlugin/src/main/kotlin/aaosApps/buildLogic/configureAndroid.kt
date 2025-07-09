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

package aaosApps.buildLogic

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project

/**
 * This function configures common Android properties for the project.
 *
 * @param buildCfgExt The extension containing the build configuration.
 */
fun Project.configureCommonAndroid(buildCfgExt: AaosAppsBuildCfgExt) {
    // This block will trigger after an Android plugin is loaded
    extensions.getByType(com.android.build.api.dsl.CommonExtension::class.java).apply {
        defaultConfig { missingDimensionStrategy("overlayable", "overlayable") }
        val ace = project.extensions.getByType(AndroidComponentsExtension::class.java)

        compileSdk = buildCfgExt.currentSdk.get()
        buildToolsVersion = project.findProperty("aaosApps.buildCfg.buildToolsVersion")!!.toString()

        // NDK: Check if we're using the prebuilt SDK, and if so set the path to the
        // prebuilt NDK
        val sdkDir = ace.sdkComponents.sdkDirectory.get().asFile
        if (sdkDir.path.contains("prebuilts/fullsdk")) {
            ndkPath = sdkDir.resolve("ndk-bundle").absolutePath
        }

        // Load the NDK version from the Gradle properties.
        // When building with the prebuilt NDK, this must match the prebuilt NDK's version
        ndkVersion = project.findProperty("aaosApps.buildCfg.ndkVersion")!!.toString()

        // The default location for the native build directory is relative to the module,
        // which mucks up the git staging. We need to move it out of the module, but it
        // can't be within the build directory, so the below line creates a
        // cmake-build-staging directory in the same directory that holds the rest of the
        // build directories (out/aaos-apps-gradle-build)
        externalNativeBuild.cmake {
            // Load the CMake version from the Gradle properties.
            version = project.findProperty("aaosApps.buildCfg.cmakeVersion")!!.toString()
            buildStagingDirectory =
                project.rootProject.layout.buildDirectory
                    .dir("../cmake-build-staging/${project.name}")
                    .get()
                    .asFile
        }
    }
}

/**
 * Configures an Android application project.
 *
 * This function applies configurations specific to Android application modules, including setting
 * the `versionCode` and `versionName` for the application. It attempts to read these values from
 * the project's properties; if not found, it defaults to values derived from the
 * `buildCfgExt.currentSdk`.
 *
 * @param buildCfgExt The extension containing build configuration details, particularly the target
 *   SDK version.
 */
fun Project.configureAndroidApp(buildCfgExt: AaosAppsBuildCfgExt) {
    extensions.getByType(com.android.build.gradle.AppExtension::class.java).apply {
        defaultConfig {
            val versionCode: Int? = project.properties["versionCode"]?.toString()?.toInt()
            it.versionCode = versionCode ?: buildCfgExt.currentSdk.get()
            val versionName: String? = project.properties["versionName"]?.toString()
            it.versionName = versionName ?: buildCfgExt.currentSdk.get().toString()
        }
    }
}
