#!/bin/sh
# Release build posture (F21, ADR-0021).
#
# The app had no `buildTypes` block at all. AGP's defaults happen to be right for release, but a
# default is not a decision — and nothing asserted it, so a one-line edit could have shipped a
# debuggable release without any gate noticing. A debuggable build is reachable by `run-as` and
# `adb pull`: its entire private data directory, including the encrypted session store, is readable
# by anyone with the phone and a cable.
#
# What this gate does NOT do, said so it is not assumed: it checks the SOURCE declaration, not the
# built artifact. Asserting on the APK needs a build and belongs with the device checks.
#
# Rehearsed against bait before being trusted.

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

GRADLE="androidApp/build.gradle.kts"
FAIL=0
fail() { FAIL=1; echo ""; echo "FAIL $1"; echo "  $2"; }

[ -f "$GRADLE" ] || { echo "FAIL $GRADLE is missing"; exit 1; }

# Comments stripped: this file explains what a debuggable release would mean, and the explanation
# must not read as the configuration (the mistake three earlier gates in this repo made).
CODE=$(python3 - "$GRADLE" <<'PY'
import re, sys, pathlib
src = pathlib.Path(sys.argv[1]).read_text()
src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
print(re.sub(r"//[^\n]*", " ", src))
PY
)

echo "$CODE" | grep -q 'buildTypes' ||
    fail "no buildTypes block" "Release posture would be inherited from AGP defaults (ADR-0021)."

release=$(echo "$CODE" | sed -n '/getByName("release")/,/^        }/p')
[ -n "$release" ] ||
    fail "no release build type declared" "It must state its own posture (ADR-0021)."

echo "$release" | grep -q 'isDebuggable = false' ||
    fail "the release build does not declare isDebuggable = false" \
         "A debuggable release exposes the whole private data dir to run-as and adb pull (ADR-0021)."

echo "$release" | grep -q 'isDebuggable = true' &&
    fail "the release build is DEBUGGABLE" "This is the shipped artifact (ADR-0021)."

if [ $FAIL -eq 0 ]; then
    echo "release-hardening: OK"
else
    exit 1
fi
