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

plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`

    // Apply the Kotlin JVM plugin to add support for Kotlin.
    alias(libs.plugins.kotlinJvm)

    alias(libs.plugins.ktfmt)
}

kotlin { jvmToolchain(21) }

ktfmt { kotlinLangStyle() }

gradlePlugin {
    // Define the plugins
    val projectPlugin by
        plugins.creating {
            id = "aaosApps.buildLogic"
            implementationClass = "aaosApps.buildLogic.ProjectPlugin"
        }
    val localPublishPlugin by
        plugins.creating {
            id = "aaosApps.localPublish"
            implementationClass = "aaosApps.buildLogic.localPublishPlugin"
        }
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ktfmt.gradlePlugin)
}

testing {
    suites {
        // Configure the built-in test suite
        val test by
            getting(JvmTestSuite::class) {
                // Use Kotlin Test test framework
                useKotlinTest(libs.versions.kotlin.get())
            }

        // Create a new test suite
        val functionalTest by
            registering(JvmTestSuite::class) {
                // Use Kotlin Test test framework
                useKotlinTest(libs.versions.kotlin.get())

                dependencies {
                    // functionalTest test suite depends on the production code in tests
                    implementation(project())
                }

                targets {
                    all {
                        // This test suite should run after the built-in test suite has run its
                        // tests
                        testTask.configure { shouldRunAfter(test) }
                    }
                }
            }
    }
}

gradlePlugin.testSourceSets.add(sourceSets["functionalTest"])

tasks.named<Task>("check") {
    // Include functionalTest as part of the check lifecycle
    dependsOn(testing.suites.named("functionalTest"))
}
