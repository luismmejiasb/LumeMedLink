#!/bin/sh
# Device/toolchain verifier: does a KOTLIN-ONLY change reach the iOS binary? (ADR-0030)
#
# THE PREMISE, measured before the fix existed: it did not. The Kotlin framework is STATIC and is
# pulled in by `OTHER_LDFLAGS = -framework LumeMedLink` plus a search path, so nothing Xcode tracks
# points at it. Change only Kotlin and Gradle rebuilds the framework while the app binary stays
# BYTE-IDENTICAL — same sha256, same mtime, old code, build green. Every iOS observation made after
# a Kotlin-only edit measured the PREVIOUS build.
#
# This is the second half of the staleness F7 found. Setting KOTLIN_FRAMEWORK_BUILD_TYPE fixed
# Gradle's OUTPUT; it did nothing about the LINK. Fixing one link of a chain and not checking the
# next is how this repo got the same defect twice.
#
# WHY A VERIFIER AND NOT A GATE: no grep can answer "did the linker re-run". check-ios-host.sh
# asserts the MECHANISM is present (ADR-0029); only this script asserts it WORKS, and it costs four
# Xcode builds, so it runs by hand — before trusting any iOS measurement, and after touching the
# build phase.
#
# It never touches the working tree: everything happens in a throwaway git worktree.
#
# Usage: Scripts/verify-ios-link-freshness.sh

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

command -v xcodebuild >/dev/null 2>&1 || { echo "verify: xcodebuild is required"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "verify: not a git repository"; exit 1; }

WORK=$(mktemp -d "${TMPDIR:-/tmp}/lume-link.XXXXXX") || exit 1
TREE="$WORK/tree"
cleanup() {
    git worktree remove --force "$TREE" >/dev/null 2>&1
    rm -rf "$WORK"
}
trap cleanup EXIT INT TERM

echo "verify-ios-link-freshness: preparing a worktree (this runs four Xcode builds)"
git worktree add --detach -q "$TREE" HEAD >/dev/null 2>&1 || { echo "verify: could not create a worktree"; exit 1; }

# The working tree, not HEAD: this must measure what you have now, or it reports on a fix you have
# not committed yet. (The bait rehearsal learned this the expensive way.)
git diff HEAD --binary > "$WORK/local.patch" 2>/dev/null
if [ -s "$WORK/local.patch" ]; then
    (cd "$TREE" && git apply --whitespace=nowarn "$WORK/local.patch") ||
        { echo "verify: could not carry your uncommitted changes into the worktree"; exit 1; }
    echo "  (uncommitted changes carried over)"
fi

SCREENS="$TREE/composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/Screens.kt"
DD="$WORK/dd"
BIN="$DD/Build/Products/Debug-iphonesimulator/LumeMedLink.app/LumeMedLink.debug.dylib"
ALT="$DD/Build/Products/Debug-iphonesimulator/LumeMedLink.app/LumeMedLink"

build() {
    (cd "$TREE" && xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
        -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
        -derivedDataPath "$DD" CODE_SIGNING_ALLOWED=NO build) > "$WORK/$1.log" 2>&1
}

# The marker is a Kotlin string literal, so it lands in the binary as UTF-16 — `strings` is blind
# to it, which cost an hour the first time. Search the bytes.
marker_in_binary() {
    python3 - "$1" "$BIN" "$ALT" <<'PY'
import pathlib, sys
needle = sys.argv[1].encode("utf-16-le")
for candidate in sys.argv[2:]:
    p = pathlib.Path(candidate)
    if p.is_file() and needle in p.read_bytes():
        print("PRESENT")
        raise SystemExit(0)
print("ABSENT")
PY
}

set_marker() {
    python3 - "$SCREENS" "$1" "$2" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
p.write_text(p.read_text().replace(sys.argv[2], sys.argv[3]))
PY
}

echo "  build 1/4 — baseline"
build baseline || { echo "FAIL: the baseline build did not succeed"; tail -20 "$WORK/baseline.log"; exit 1; }

echo "  build 2/4 — after a KOTLIN-ONLY change, WITH the relink guard"
set_marker '"LumeMedLink"' '"LUMEFRESHNESSPROBE1"'
build withguard || { echo "FAIL: the guarded build did not succeed"; tail -20 "$WORK/withguard.log"; exit 1; }
GUARDED=$(marker_in_binary LUMEFRESHNESSPROBE1)

# ── THE LIVE CONTROL ────────────────────────────────────────────────────────────────────────────
# Strip the relink guard out of the build phase and run the SAME experiment. If the control does
# not reproduce the hole, this script is not measuring what it claims and its green means nothing.
echo "  build 3/4 — control: the same change with the guard REMOVED"
python3 - "$TREE/iosApp/iosApp.xcodeproj/project.pbxproj" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
s = p.read_text()
before = s
s = s.replace('rm -f \\"$TARGET_BUILD_DIR/$EXECUTABLE_PATH\\"\\n', '')
s = s.replace('rm -f \\"$TARGET_BUILD_DIR/$EXECUTABLE_FOLDER_PATH\\"/*.debug.dylib\\n', '')
if s == before:
    raise SystemExit("the control could not be applied: the relink guard was not found")
p.write_text(s)
PY
[ $? -eq 0 ] || { echo "FAIL: could not build the control"; exit 1; }
rm -rf "$DD"
build controlbase || { echo "FAIL: the control baseline build did not succeed"; exit 1; }
echo "  build 4/4 — control: a second Kotlin-only change, unguarded"
set_marker '"LUMEFRESHNESSPROBE1"' '"LUMEFRESHNESSPROBE2"'
build control || { echo "FAIL: the control build did not succeed"; exit 1; }
UNGUARDED=$(marker_in_binary LUMEFRESHNESSPROBE2)

echo ""
echo "── WITH the relink guard (production behaviour) ──"
echo "  Kotlin-only change reached the binary : $GUARDED        <- THE FIX"
echo "── LIVE CONTROL — guard removed ──"
echo "  Kotlin-only change reached the binary : $UNGUARDED         <- THE HOLE"
echo ""

if [ "$GUARDED" = "PRESENT" ] && [ "$UNGUARDED" = "ABSENT" ]; then
    echo "verify-ios-link-freshness: OK — the guard works and the control reproduces the hole"
    exit 0
fi
if [ "$UNGUARDED" = "PRESENT" ]; then
    echo "verify-ios-link-freshness: INCONCLUSIVE — the control did NOT reproduce the hole."
    echo "  Xcode may have relinked for an unrelated reason; this run proves nothing either way."
    exit 1
fi
echo "verify-ios-link-freshness: FAILED — a Kotlin-only change did not reach the binary."
exit 1
