#!/bin/sh
# One logging path (F22, ADR-0020).
#
# §8.1 says the redacting facade is the SINGLE point of logging. Nothing enforced it: until F20,
# `org.slf4j` shipped in the release APK and `android.util.Log` was importable anywhere.
#
# WHY LOGGING IS A SECURITY SURFACE HERE, and not hygiene: anything written to logcat at ANY
# priority leaves the device inside `adb bugreport` — a zip the doctor can send to anyone, on a
# RELEASE build, surviving a reboot. One stray `Log.d(TAG, patient.toString())` in a future slice
# is a patient record in a support attachment.
#
# The import-level ban lives in detekt (ForbiddenImport, with core/ exempt). This gate covers what
# an import ban cannot: fully-qualified calls, and platform logging that needs no import at all.
#
# Rehearsed against bait before being trusted.

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

SRC=$(find composeApp/src androidApp/src -mindepth 1 -maxdepth 1 -type d 2>/dev/null | tr '\n' ' ')
KT_FILES=$(find $SRC -name '*.kt' 2>/dev/null)
FAIL=0

if [ -z "$KT_FILES" ]; then
    echo "FAIL logging gate scanned NOTHING — no .kt files under: $SRC"
    exit 1
fi

FLAT=$(mktemp)
trap 'rm -f "$FLAT"' EXIT
python3 - "$FLAT" $KT_FILES <<'PY'
import re, sys, pathlib
out = open(sys.argv[1], "w")
for f in sys.argv[2:]:
    src = pathlib.Path(f).read_text(errors="replace")
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    out.write(f + "\t" + re.sub(r"\s+", " ", src) + "\n")
out.close()
PY

report() {
    hits=$(grep -oE ".{0,50}$1" "$FLAT" || true)
    if [ -n "$hits" ]; then
        FAIL=1
        echo ""
        echo "FAIL logging: $2"
        echo "  $3"
        echo "$hits" | sed 's/^/    /'
    fi
}

report 'android\.util\.Log\.[a-z]' "a logcat write" \
    "It leaves the device in a bug report, on release builds (§8.1). Use core/logging's sink."
report '\bprintln\(|\bprint\(' "a println" \
    "On Android it becomes a logcat line; on iOS it reaches the device console (§8.1)."
report 'System\.(out|err)\.print' "a stdout/stderr write" "Same destination, different spelling (§8.1)."
report 'NSLog\(|os_log' "an Apple system log write" "It lands in the device console and sysdiagnose (§8.1)."
report 'LoggerFactory\.getLogger|org\.slf4j\.' "an slf4j logger" \
    "slf4j-api ships in the release APK; a second logging path bypasses the redacting sink (§8.1)."
report 'e\.printStackTrace\(\)' "printStackTrace" \
    "An exception message can carry a URL or a server string — this repo has had exactly that leak (§8.1)."

if [ $FAIL -eq 0 ]; then
    echo "logging: OK (one sink, $(echo "$KT_FILES" | wc -w | tr -d ' ') files)"
else
    exit 1
fi
