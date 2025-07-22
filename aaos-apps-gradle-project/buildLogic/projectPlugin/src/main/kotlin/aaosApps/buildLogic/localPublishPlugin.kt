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

import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.api.AndroidBasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

class localPublishPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.apply("maven-publish")

        project.plugins.withType(AndroidBasePlugin::class.java) {
            project.extensions.getByType(LibraryExtension::class.java).publishing {

                // Bit hacky, but car-ui-lib needs to have its own publishing config and I don't
                // want to duplicate the repository definition below.
                if (project.name != "car-ui-lib" && project.name != "car-rotary-lib") {
                    singleVariant("release") { withSourcesJar() }
                }
            }
        }
        project.extensions.getByType(PublishingExtension::class.java).apply {
            repositories {
                it.maven { repo ->
                    repo.name = "local"
                    repo.url =
                        project.uri(
                            // We want the m2repo to be at the base of the build output.
                            project.rootProject.layout.buildDirectory.dir("../unbundled_m2repo")
                        )
                }
            }
            if (project.name != "car-ui-lib" && project.name != "car-rotary-lib") {
                publications {
                    it.register("release", MavenPublication::class.java) { pub ->
                        pub.groupId = "com.android.car"
                        pub.version = "UNBUNDLED"
                        project.afterEvaluate { pub.from(project.components.getByName("release")) }
                    }
                }
            }
        }
    }
}
