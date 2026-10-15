#!/bin/sh
# Copyright 2026 The Compukters Developers
# SPDX-License-Identifier: Apache-2.0
# Adapted for Orbitalis: independent tooling namespace and repository prerequisites.
set -eu

python3 "$ORBITALIS_BUILD_ROOT/tools/gradle/cache.py" --root "$ORBITALIS_BUILD_ROOT" prepare
if flock --shared "$GRADLE_USER_HOME/.orbitalis-cache.lock" \
    "$ORBITALIS_BUILD_GRADLE" --no-daemon --project-cache-dir "$ORBITALIS_BUILD_ROOT/.gradle-agent" -Dorg.gradle.vfs.watch=false "$@"; then
    python3 "$ORBITALIS_BUILD_ROOT/tools/gradle/cache.py" --root "$ORBITALIS_BUILD_ROOT" finish || \
        printf 'Gradle succeeded; its cache template could not be refreshed.\n' >&2
else
    exit "$?"
fi
