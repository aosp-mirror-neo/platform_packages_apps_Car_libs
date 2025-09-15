#!/bin/bash
set -eou pipefail

SCRIPTS_DIR=$(realpath "${0%/*}")
. "$SCRIPTS_DIR/envsetup.sh"

# Functions defined in envsetup.sh
setup_build_environment

# Run the build
# (Keep each line separate to keep merges clean)
# Adding the javaToolchains task to log the JDKs that the build can see
./gradlew \
    :buildLogic:javaToolchains \
    test

# Functions defined in envsetup.sh
wrap_up_build $?
