*All additions to this file must be as brief as possible in order to preserve model context.*

# Car Libraries Overview

This directory contains shared libraries and common code for AOSP Car applications.

## Working Directory (**HUMANS READ THIS**)

When working with changes that affect multiple projects, gemini must be launched from `packages/apps/Car` so it can write to all files.

## Formatting
All code must follow the [AOSP Java](https://source.android.com/docs/setup/contribute/code-style) and [Kotlin](https://source.android.com/docs/setup/contribute/code-style) style guides.

-   **Java Style**: 4-space indent, 100-char lines, full imports, standard braces, acronyms as words.
-   **Kotlin Style**: 4-space indent, UpperCamelCase for classes, camelCase for functions/properties, space after colons.

## Commit Messages

Commit messages should follow this format:

```
A brief, one-line summary

A more detailed description of the changes.

Bug: [Bug number]
Test: [How you tested the change]
Relnote: [Release note, or "N/A"]
```

The `Change-Id` is added automatically by a pre-commit hook.
Backticks (`) must be escaped when committing via shell.

## Directory Structure

-   `aaos-apps-gradle-project/`: Gradle project for building car apps and libraries.
-   `car-app-card-host-lib/`: Library for hosting app cards.
-   `car-app-card-lib/`: Library for implementing app cards.
-   `car-apps-common/`: Common code and resources for car apps.
-   `car-assist-lib/`: Library for voice assistant integration.
-   `car-broadcastradio-support/`: Helpers for the broadcast radio HAL.
-   `car-media-common/`: Common components for media apps.
-   `car-media-extensions/`: Extensions for media functionality.
-   `car-messenger-common/`: Common code for messaging apps.
-   `car-telephony-common/`: Shared code for telephony apps.
-   `car-testing-common/`: Common testing utilities.
-   `car-ui-lib/`: Core UI components for car apps.
-   `car-uxr-client-lib/`: Client library for UXR (User Experience Restrictions).
-   `certs/`: Development signing certificates.
-   `tools/`: Development helper scripts.
