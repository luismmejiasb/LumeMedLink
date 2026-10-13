#!/bin/sh
# Compose resources reach the Android APK (2026-10-12).
#
# What this gate exists to prevent was MEASURED on an emulator, not assumed: with the AGP KMP
# library plugin, Android resources are off by default, and Compose Multiplatform 1.11.1 then leaves
# `copyAndroidMainComposeResourcesToAndroidAssets` without an output directory. The task fails, the
# packaging does not wait for it, and the APK ships WITHOUT the app's strings: the app crashed at its
# first string on Android while every host test stayed green — host tests never package an APK.
#
# The control is one line in composeApp/build.gradle.kts, inside `kotlin { android { } }`:
#     androidResources { enable = true }
# Asserted as the mechanism, with comments blanked by the tokenizer (ADR-0029): a commented-out line
# or `enable = false` is a failure.
#
# Rehearsed against bait before being trusted (Scripts/rehearse-gates.sh).
#
# Usage: Scripts/check-compose-resources-packaging.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

BUILD="composeApp/build.gradle.kts"
CODE=$(python3 Scripts/lib/uncomment.py --lang c --flatten "$BUILD" 2>/dev/null)

if ! printf '%s\n' "$CODE" | grep -qE 'androidResources[[:space:]]*\{[[:space:]]*enable[[:space:]]*=[[:space:]]*true[[:space:]]*\}'; then
    echo ""
    echo "FAIL compose-resources: Android resources are not enabled in the KMP target"
    echo "  $BUILD must say androidResources { enable = true } inside kotlin { android { } }."
    echo "  Without it the APK ships without the app's strings and crashes at the first one."
    exit 1
fi
echo "compose-resources-packaging: OK"
