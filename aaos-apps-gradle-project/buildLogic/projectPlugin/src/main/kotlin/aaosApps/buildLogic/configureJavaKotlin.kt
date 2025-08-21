/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.build.gradle.api.AndroidBasePlugin
import com.ncorti.ktfmt.gradle.KtfmtExtension
import com.ncorti.ktfmt.gradle.KtfmtPlugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.CompileClasspathNormalizer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

/**
 * Configures Java compilation settings for the AAOS apps project.
 *
 * This function performs the following actions:
 * - Sets the Java toolchain version based on the provided configuration.
 * - Enables specific compiler warnings related to unchecked operations and deprecation.
 * - Configures the classpath for Java compilation, Adding Android system stubs.
 *
 * @param buildCfg The configuration extension for AAOS apps.
 */
internal fun Project.configureJava(buildCfg: AaosAppsBuildCfgExt) {

    // Set the toolchains for the project
    plugins.withType(JavaBasePlugin::class.java) {
        extensions.getByType(JavaPluginExtension::class.java).apply {
            toolchain.languageVersion.set(buildCfg.jdkToolchain.map(JavaLanguageVersion::of))
        }
    }

    // Enable some lints
    tasks.withType(JavaCompile::class.java).configureEach { task ->
        // TODO: just pass -Xlint, to enable all the supported types of warnings.
        task.options.compilerArgs.add("-Xlint:unchecked")
        task.options.compilerArgs.add("-Xlint:deprecation")
    }

    // Add in the System stubs and other Java compile configuration
    val currentSdk = buildCfg.currentSdk.get()
    val systemStubs = buildCfg.repoRoot.file("prebuilts/sdk/$currentSdk/system/android.jar")

    project.plugins.withType(AndroidBasePlugin::class.java) {
        tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
            task ->
            // Add the stubs as the first library in the classpath
            task.libraries.setFrom(project.files(systemStubs, task.libraries.files))
        }
        // JavaCompile uses a hard-set file collection, so we need to add the system stubs as part
        // of the task
        tasks.withType(JavaCompile::class.java).configureEach { task ->
            // Get a reference to the ObjectFactory at configuration time
            val objectFactory = project.objects
            // Add the system stubs as a task input with a normalized path
            task.inputs.files(systemStubs).withNormalizer(CompileClasspathNormalizer::class.java)
            // Now we can add the system stubs to the classpath
            task.doFirst {
                task.classpath = objectFactory.fileCollection().from(systemStubs, task.classpath)
            }
        }
    }
}

/**
 * Configures Kotlin compilation settings for the AAOS apps project.
 *
 * This function applies the Ktfmt plugin for code formatting and sets the Java toolchain version
 * for Kotlin compilation based on the provided configuration.
 *
 * @param buildCfg The configuration extension for AAOS apps.
 */
internal fun Project.configureKotlin(buildCfg: AaosAppsBuildCfgExt) {
    // Apply the Ktfmt plugin to the project and set the style properly
    project.plugins.apply(KtfmtPlugin::class.java)
    project.extensions.getByType(KtfmtExtension::class.java).apply { kotlinLangStyle() }

    // Set the Java toolchain
    kotlinExtension.jvmToolchain {
        it.languageVersion.set(buildCfg.jdkToolchain.map(JavaLanguageVersion::of))
    }
}
