#!/bin/sh
# Device verifier: does the app-switcher snapshot carry anything? (F1 on iOS, ADR-0010/0031)
#
# iOS has no FLAG_SECURE. Apple exposes no way to refuse the snapshot it takes when the app leaves
# the foreground, and that snapshot is what the switcher shows AND what is written to disk, inside
# this app's own container. The only defence is to make sure there is nothing on it.
#
# WHY THIS SCRIPT EXISTS AT ALL. Bitácora 0023 concluded the Simulator could not verify this,
# because the switcher card was blank with both covers disabled. ADR-0028 withdrew that conclusion
# as unproven once the stale framework came to light. Both were arguing about a card on a screen.
# The artifact is on disk: iOS writes the snapshot to
#   Library/SplashBoard/Snapshots/sceneID:<bundle>-default/*.ktx
# and a KTX of a flat colour compresses to a fraction of one carrying content. You do not have to
# decode it — the SIZE, against a live control, is the measurement.
#
# THREE CONDITIONS, because there are two covers and one of them can hide the other's failure:
#   1. production      both covers            -> snapshot must be flat
#   2. host cover only Compose overlay off    -> still flat, so the host window works ALONE
#   3. neither         the live control       -> must carry content, or this proves nothing
#
# Condition 2 is the one that matters most: the host window exists for what Compose cannot cover
# (an alert, a share sheet, anything presented above it), and with the Compose overlay working its
# failure is invisible. It failed silently for exactly that reason until 2026-09-21.
#
# It never touches the working tree: everything happens in a throwaway git worktree.
#
# Usage: Scripts/verify-ios-privacy-cover.sh   (needs a booted iOS simulator; runs three builds)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

BUNDLE_ID=com.luismejias.lumemedlink
DEVICE=$(xcrun simctl list devices booted 2>/dev/null | sed -n 's/.*(\([0-9A-F-]\{36\}\)) (Booted).*/\1/p' | head -1)
[ -n "$DEVICE" ] || { echo "verify: boot an iOS simulator first (xcrun simctl boot '<device>')"; exit 1; }
echo "verify-ios-privacy-cover: simulator $DEVICE"

WORK=$(mktemp -d "${TMPDIR:-/tmp}/lume-cover.XXXXXX") || exit 1
TREE="$WORK/tree"
cleanup() {
    git worktree remove --force "$TREE" >/dev/null 2>&1
    rm -rf "$WORK"
}
trap cleanup EXIT INT TERM

git worktree add --detach -q "$TREE" HEAD >/dev/null 2>&1 || { echo "verify: could not create a worktree"; exit 1; }
git diff HEAD --binary > "$WORK/local.patch" 2>/dev/null
if [ -s "$WORK/local.patch" ]; then
    (cd "$TREE" && git apply --whitespace=nowarn "$WORK/local.patch") || { echo "verify: could not carry uncommitted changes"; exit 1; }
    echo "  (uncommitted changes carried over)"
fi

COMPOSE_COVER="$TREE/composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/PrivacyScreen.kt"
HOST_COVER="$TREE/iosApp/iosApp/AppDelegate.swift"

disable_compose_cover() {
    python3 - "$COMPOSE_COVER" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1]); s = p.read_text()
old = "internal fun shouldCover(state: Lifecycle.State): Boolean = state != Lifecycle.State.RESUMED"
if old not in s:
    raise SystemExit("could not disable the Compose cover: shouldCover not found")
p.write_text(s.replace(old, "internal fun shouldCover(state: Lifecycle.State): Boolean = false"))
PY
}

disable_host_cover() {
    python3 - "$HOST_COVER" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1]); s = p.read_text()
old = "self?.showPrivacyCover(on: notification.object as? UIWindowScene)"
if old not in s:
    raise SystemExit("could not disable the host cover: the willDeactivate handler not found")
p.write_text(s.replace(old, "()"))
PY
}

# Returns the largest scene snapshot, in bytes. The scene snapshots are the app's screen; the ones
# under "{DEFAULT GROUP}" are the launch image and do not move between conditions.
largest_scene_snapshot() {
    container=$(xcrun simctl get_app_container "$DEVICE" "$BUNDLE_ID" data 2>/dev/null)
    [ -n "$container" ] || { echo 0; return; }
    find "$container/Library/SplashBoard/Snapshots" -path "*sceneID*" -name '*.ktx' -exec stat -f '%z' {} \; 2>/dev/null |
        sort -n | tail -1 | grep . || echo 0
}

measure() {
    label=$1
    (cd "$TREE" && xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
        -configuration Debug -sdk iphonesimulator -destination "id=$DEVICE" \
        -derivedDataPath "$WORK/dd" CODE_SIGNING_ALLOWED=NO build) > "$WORK/$label.log" 2>&1 ||
        { echo "FAIL: the $label build did not succeed"; tail -20 "$WORK/$label.log"; exit 1; }
    xcrun simctl terminate "$DEVICE" "$BUNDLE_ID" >/dev/null 2>&1
    xcrun simctl uninstall "$DEVICE" "$BUNDLE_ID" >/dev/null 2>&1
    xcrun simctl install "$DEVICE" "$WORK/dd/Build/Products/Debug-iphonesimulator/LumeMedLink.app" >/dev/null 2>&1
    xcrun simctl launch "$DEVICE" "$BUNDLE_ID" >/dev/null 2>&1
    sleep 8
    # Anything else in the foreground makes the system snapshot this app on its way out.
    xcrun simctl launch "$DEVICE" com.apple.Preferences >/dev/null 2>&1
    sleep 7
    largest_scene_snapshot
}

echo "  build 1/3 — production: both covers"
BOTH=$(measure production)
echo "  build 2/3 — host window alone: the Compose overlay disabled"
disable_compose_cover
HOST_ONLY=$(measure hostonly)
echo "  build 3/3 — LIVE CONTROL: neither cover"
disable_host_cover
NEITHER=$(measure control)

echo ""
echo "  largest scene snapshot on disk, in bytes:"
echo "    production (both covers)        : $BOTH"
echo "    host window alone               : $HOST_ONLY"
echo "    NEITHER cover  (the control)    : $NEITHER      <- must be the big one"
echo ""

if [ "$NEITHER" -le "$BOTH" ] || [ "$NEITHER" -le "$HOST_ONLY" ]; then
    echo "verify-ios-privacy-cover: INCONCLUSIVE — the control did not carry more than the covered runs."
    echo "  Nothing is proven either way: a control that cannot fail is not a control."
    exit 1
fi
if [ $((NEITHER / 2)) -le "$BOTH" ] || [ $((NEITHER / 2)) -le "$HOST_ONLY" ]; then
    echo "verify-ios-privacy-cover: FAILED — a covered snapshot is within 2x of the uncovered one."
    echo "  A flat colour compresses to a fraction; this is not a flat snapshot."
    exit 1
fi
echo "verify-ios-privacy-cover: OK — both layers flatten the snapshot, and the control reproduces the hole"
