#!/bin/bash
set -eou pipefail

SCRIPTS_DIR=$(realpath "${0%/*}")
. "$SCRIPTS_DIR/envsetup.sh"

# Functions defined in envsetup.sh
setup_build_environment

DEFAULT_MAVEN_URL="https://repo.maven.apache.org/maven2"
if [ -n "${1-}" ]; then
  export MAVEN_CENTRAL_URL="$1"
else
  export MAVEN_CENTRAL_URL="$DEFAULT_MAVEN_URL"
fi

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
