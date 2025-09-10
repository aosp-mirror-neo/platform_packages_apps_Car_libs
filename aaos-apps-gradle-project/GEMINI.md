*All additions to this file must be as brief as possible in order to preserve model context.*

# AAOS Apps Gradle Project

This directory contains a Gradle project for building AAOS applications and libraries from within the AOSP source tree.

## Working Directory (**HUMANS READ THIS**)

When working with changes that affect multiple projects, gemini must be launched from `packages/apps/Car` so it can write to all files.

## Project Structure

This Gradle project includes applications and libraries from various directories within the AOSP checkout. The `settings.gradle.kts` file maps Gradle project names to their source code paths. For example:

```kotlin
":car-app-card-host-lib" to "../car-app-card-host-lib/app-card-host",
":car-bugreport-app" to "../../BugReport",
```

## Build Logic

The `buildLogic` directory contains a convention plugin that applies common build configurations to all included projects, including toolchain versions, compiler options, and code formatting.

## Running Tests

Always prefer to use project-specific and focused commands to run builds and tests
  E.g.: after making changes in `car-media-common`, run `./gradlew :car-media-common:assembleDebug` instead of `./gradlew assemble`. Same goes for tests.

The `busytown/` directory contains scripts for running build and test tasks, primarily for CI.

-   `build-and-test.sh`: Builds all projects and runs all tests.
-   `run-host-unit-tests.sh`: Runs only host-based unit tests.
-   `smoke-test.sh`: Runs a small set of tasks to quickly verify changes to the Gradle build scripts.

Don't run Gradle project-wide builds or tests unless specifically asked to, but request to run them when appropriate, such as after finishing a task.
Do not run `build` or `lint` tasks. Most of the project does not pass lints.

## Modern Gradle Practices

This project uses modern Gradle practices like configuration avoidance and project isolation. When modifying the build, prefer `register` over `create` for tasks and configurations, and use convention plugins to share logic instead of cross-project configuration. For more details, see the [Gradle documentation](https://docs.gradle.org/). Remember to consult the docs if the Gradle version is newer than one you're familiar with.
