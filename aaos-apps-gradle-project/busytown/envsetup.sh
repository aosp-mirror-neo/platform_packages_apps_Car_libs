#!/bin/bash
set -eou pipefail

# Resolve various absolute paths
SCRIPTS_DIR=$(realpath "${0%/*}")

setup_build_environment() {
    GRADLE_PROJ_DIR=$(realpath "$SCRIPTS_DIR/..")
    ROOT_DIR=$(realpath "$SCRIPTS_DIR/../../../../../../")

    OS=linux
    if [[ $(uname) = "Darwin" ]]; then OS="darwin"; fi
    ARCH="x86"
    case "$(arch)" in
      arm64* )
        ARCH="arm64"
    esac

    ANDROID_HOME="$ROOT_DIR/prebuilts/fullsdk-$OS"
    echo "Setting ANDROID_HOME to $ANDROID_HOME"

    # Make the locations of the JDKs available to the build
    AOSP_JDK17="$ROOT_DIR/prebuilts/jdk/jdk17/${OS}-${ARCH}"
    AOSP_JDK21="$ROOT_DIR/prebuilts/jdk/jdk21/${OS}-${ARCH}"
    JAVA_HOME="$AOSP_JDK21"

    # Disable the build daemon, java auto-downloading and caching (no point when running in CI
    # Either set Gradle opts or prepend to it with a comma (separator) if it exists
    GRADLE_OPTS="
      -Dorg.gradle.daemon=false
      -Dorg.gradle.java.installations.auto-detect=false
      -Dorg.gradle.java.installations.auto-download=false
      -Dorg.gradle.caching=false
      -Dorg.gradle.configuration-cache=false
      ${GRADLE_OPTS:+,${GRADLE_OPTS}}"

    # Export everything we need
    export ANDROID_HOME
    export GRADLE_PROJ_DIR
    export GRADLE_OPTS
    export AOSP_JDK17
    export AOSP_JDK21
    export JAVA_HOME

    echo "Starting $0 at $(date)"

    # We must overwrite local.properties to specify the SDK and CMake directories.
    # Back up the original local.properties file if it exists, it'll be restored
    # after the build is complete.
    if [ -f "$GRADLE_PROJ_DIR/local.properties" ]; then
        mv "$GRADLE_PROJ_DIR/local.properties" "$GRADLE_PROJ_DIR/backup_local.properties"
        echo "local.properties renamed to backup_local.properties"
    else
        echo "local.properties not found."
    fi

    echo "sdk.dir=$ANDROID_HOME" >> "$GRADLE_PROJ_DIR/local.properties"
    echo "cmake.dir=$ANDROID_HOME/native-build-tools" >> "$GRADLE_PROJ_DIR/local.properties"

    cd "$GRADLE_PROJ_DIR"

    # Disable exit on error so we can handle a failed build
    set +e
}

wrap_up_build() {
    RC=$1

    # Restore the original local.properties file (if there was one)
    if [ -f "$GRADLE_PROJ_DIR/backup_local.properties" ]; then
        # Rename local.properties to backup_local.properties
        mv "$GRADLE_PROJ_DIR/backup_local.properties" "$GRADLE_PROJ_DIR/local.properties"
        echo "Restored local.properties"
    else
        echo "Unable to restore local.properties. Re-launch Android Studio to recreate it"
    fi

    echo "Completing $0 at $(date)"
    if [[ $RC != 0 ]]; then
        echo "check_status: non-zero exit code -> $RC. Failing out"
        exit "$RC"
    else
        echo "check_status: exit code $RC is good. Continuing"
    fi
}
