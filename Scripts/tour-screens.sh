#!/bin/bash
# Saves a screenshot of each of the patient's wired screens (task 0037), by running the ScreenTour UI test on the
# repo's own simulator. Android cannot be pictured: every window with personal data is FLAG_SECURE (§8.3).
#
#   Scripts/tour-screens.sh [--device <udid>] [--out <folder>]
#
# The Debug build talks to the platform on this Mac (ADR-0062); start it first, or every screen shows its offline
# state. The pictures land in --out (default: ../tmp/screens-<time>), outside the repo: they are synthetic, and still
# not the repository's to keep.
set -euo pipefail
cd "$(dirname "$0")/.."

SIM_NAME="LumeMedLink · iPhone 17 · iOS 26.5"
DEVICE=""
OUT="../tmp/screens-$(date +%Y%m%d-%H%M%S)"
while [ $# -gt 0 ]; do
    case "$1" in
        --device) shift; DEVICE="${1:-}" ;;
        --out) shift; OUT="${1:-}" ;;
        *) echo "unknown argument: $1"; exit 2 ;;
    esac
    shift
done

if [ -z "$DEVICE" ]; then
    # By its name, never "the first booted one": with a session per repo, that can be another repo's (vault, 2026-10-07).
    DEVICE=$(xcrun simctl list devices available | grep -F "$SIM_NAME (" | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/' | head -1)
fi
[ -n "$DEVICE" ] || { echo "FAIL no simulator named \"$SIM_NAME\"; pass --device <udid>"; exit 1; }

curl -s -o /dev/null --max-time 3 http://127.0.0.1:3000/v1/app-availability ||
    echo "WARN the local platform does not answer on 127.0.0.1:3000: the screens will show their offline state"

mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
xcrun simctl boot "$DEVICE" 2>/dev/null || true
xcrun simctl bootstatus "$DEVICE" -b >/dev/null
# The session's tier-2 key needs an enrolled biometric (ADR-0037); the simulator enrols a face on request.
xcrun simctl spawn "$DEVICE" notifyutil -s com.apple.BiometricKit.enrollmentChanged 1
xcrun simctl spawn "$DEVICE" notifyutil -p com.apple.BiometricKit.enrollmentChanged
# A fresh install: the tour starts at the welcome screen, never on a session a previous run left.
xcrun simctl uninstall "$DEVICE" com.luismejias.lumemedlink 2>/dev/null || true

# A TEST_RUNNER_ variable in xcodebuild's environment reaches the test runner without its prefix.
TEST_RUNNER_SCREENSHOT_DIR="$OUT" xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
    -configuration Debug -destination "id=$DEVICE" -derivedDataPath ../tmp/dd-medlink \
    -only-testing:iosAppUITests/ScreenTour > "$OUT/tour.log" 2>&1 || echo "WARN the tour reported failures; see $OUT/tour.log"

ls "$OUT"/*.png >/dev/null 2>&1 || { echo "FAIL no screenshot was saved; see $OUT/tour.log"; exit 1; }
echo "screens: $OUT"
ls "$OUT"/*.png
