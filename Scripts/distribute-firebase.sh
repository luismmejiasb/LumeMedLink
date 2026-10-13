#!/bin/sh
# Firebase App Distribution (ADR-0052 §7): a release tool, run on purpose by the author, never by the build.
#
# No Gradle plugin and no tester SDK in the binary: a plugin runs with the build's privileges (ADR-0018), and an SDK
# in the app would be one more thing that talks to Google from a phone. This uploads an artifact that already exists,
# built and signed by the release process, through the Firebase CLI at a pinned version.
#
# For iOS it also uploads the dSYMs to Crashlytics, so native crashes are symbolicated. That is the job Crashlytics'
# own run script would do on every build; here it happens only on a release, by hand.
#
# Usage:
#   Scripts/distribute-firebase.sh android <path/to/app-release.apk|.aab> "<release notes>"
#   Scripts/distribute-firebase.sh ios     <path/to/LumeMedLink.ipa> "<release notes>" [path/to/dSYMs]
#
# Needs, all OUTSIDE git:
#   GOOGLE_APPLICATION_CREDENTIALS  a service-account key with the App Distribution Admin role
#   FIREBASE_ANDROID_APP_ID / FIREBASE_IOS_APP_ID   the app IDs from the Firebase console
#   FIREBASE_TESTER_GROUPS          comma-separated tester group aliases
#
# Release notes go to Google and to every tester: never a patient, a doctor, or anything about health in them.

set -eu
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT"

# The CLI's npm version, pinned: `npx firebase-tools` without a version runs whatever was published last.
# 15.33.0 was the registry's `latest` on 2026-10-13.
FIREBASE_TOOLS="firebase-tools@15.33.0"

usage() {
    sed -n '/^# Usage:/,/^# Needs/p' "$0" | sed 's/^# \{0,1\}//' | sed '$d'
    exit 2
}

[ $# -ge 3 ] || usage
platform=$1
artifact=$2
notes=$3

[ -f "$artifact" ] || { echo "distribute: no artifact at $artifact"; exit 1; }
: "${GOOGLE_APPLICATION_CREDENTIALS:?a service-account key, kept outside git}"
: "${FIREBASE_TESTER_GROUPS:?the tester group aliases}"
case "$GOOGLE_APPLICATION_CREDENTIALS" in
    "$REPO_ROOT"/*) echo "distribute: the service-account key is inside the repository; move it out"; exit 1 ;;
esac

case "$platform" in
    android) app_id=${FIREBASE_ANDROID_APP_ID:?the Android app ID} ;;
    ios) app_id=${FIREBASE_IOS_APP_ID:?the iOS app ID} ;;
    *) usage ;;
esac

if [ "$platform" = "ios" ] && [ $# -ge 4 ]; then
    dsyms=$4
    upload=$(find tmp/spm -path '*firebase-ios-sdk/Crashlytics/upload-symbols' -type f 2>/dev/null | head -1)
    plist="iosApp/iosApp/GoogleService-Info.plist"
    [ -n "$upload" ] || { echo "distribute: Crashlytics' upload-symbols not found; resolve the Swift packages first"; exit 1; }
    [ -f "$plist" ] || { echo "distribute: $plist is missing; dSYMs are matched to the app through it"; exit 1; }
    "$upload" -gsp "$plist" -p ios "$dsyms"
fi

npx --yes "$FIREBASE_TOOLS" appdistribution:distribute "$artifact" \
    --app "$app_id" \
    --groups "$FIREBASE_TESTER_GROUPS" \
    --release-notes "$notes"
