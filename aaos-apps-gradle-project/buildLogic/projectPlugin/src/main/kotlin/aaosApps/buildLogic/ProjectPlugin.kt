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

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.AndroidBasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

class ProjectPlugin : Plugin<Project> {
    /**
     * Applies the plugin to the given project.
     *
     * This function sets up the extension for the plugin, configures Java and Kotlin compilation,
     * and configures common Android settings and Android app settings based on the project type.
     *
     * @param project The project to which the plugin is applied.
     */
    override fun apply(project: Project) {
        // Set up the extension for the plugin
        val extension =
            project.extensions.create("aaosAppsBuildCfg", AaosAppsBuildCfgExt::class.java)
        extension.setDefaults(project)

        project.configureJava(extension)

        project.plugins.withType(KotlinBasePlugin::class.java) {
            project.configureKotlin(extension)
        }
        project.plugins.withType(AndroidBasePlugin::class.java) {
            project.configureCommonAndroid(extension)
        }
        project.plugins.withType(AppPlugin::class.java) { project.configureAndroidApp(extension) }

        // Disable the new Gradle 9.0 default that fails the build if a module
        // has test sources but no tests are found. This allows modules
        // with no unit tests (e.g., simple libraries) to build successfully.
        project.tasks.withType(Test::class.java) { it.failOnNoDiscoveredTests.set(false) }
    }
}
