#!/bin/bash
set -eou pipefail

SCRIPTS_DIR=$(realpath "${0%/*}")
. "$SCRIPTS_DIR/envsetup.sh"

# Functions defined in envsetup.sh
setup_build_environment

# Just a quick check to make sure that the build runs without errors
# Picking `car-ui-lib` because it doesn't rely on much
# and car-dashcam-service for the NDK build
./gradlew \
    :buildLogic:javaToolchains \
    :buildLogic:check \
    :car-ui-lib:assembleDebug \
    :car-ui-lib:assembleOverlayableDebugAndroidTest \
    :car-ui-lib:testOverlayableDebugUnitTest \
    :car-dashcam-service:assembleDebug

# Functions defined in envsetup.sh
wrap_up_build $?
