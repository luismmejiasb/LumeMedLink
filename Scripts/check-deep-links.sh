#!/bin/sh
# Deep-link posture gate (F17, §8.12) — the Android half, which had no gate at all.
#
# §8.12: only verified App Links (Android) / universal links (iOS); a custom scheme is forbidden
# because any app can claim it — the same reason as LumeMed's ADR-0021. On iOS, check-ios-host.sh
# already refuses CFBundleURLTypes. On Android NOTHING refused a `<data android:scheme="…">`: a
# filter with a custom scheme, added "for development" or brought in by a library, passed every
# gate in this repo (task 0011).
#
# Two rules, over every <intent-filter> of every component:
#   1. Every scheme an intent filter declares is `https`. Not `http` (a cleartext link), not a
#      custom one. A filter with no scheme is fine; a filter that mixes https with anything else is
#      not — Android merges a filter's <data> elements, so one bad scheme poisons the whole filter.
#   2. A browsable filter that handles https carries android:autoVerify="true": an App Link that is
#      not verified is a link any other app can also claim, which is what rule 1 exists to prevent.
#
# Read off the SOURCE manifest (our own declarations) and the MERGED one (what dependencies add —
# an OAuth library's redirect activity with a custom scheme is the classic way one arrives). PARSED,
# not grepped, for the reason ADR-0029 records: a comment cannot satisfy or fool a parser.
#
# Rehearsed against bait (Scripts/rehearse-gates.sh) before being trusted.
#
# Usage: Scripts/check-deep-links.sh   (LUME_REQUIRE_MERGED_MANIFEST=1 makes a missing build a failure)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

SRC_MANIFEST="androidApp/src/main/AndroidManifest.xml"
[ -f "$SRC_MANIFEST" ] || { echo "FAIL deep-links: $SRC_MANIFEST is missing"; exit 1; }

# Prints one line per violation, and nothing when the manifest is clean. Exit 2 on a parse error,
# which is never a pass.
violations() {
    python3 - "$1" <<'PY'
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError) as error:
    print(f"cannot parse: {error}")
    sys.exit(2)

for component in root.iter():
    for intent_filter in component.findall("intent-filter"):
        name = component.get(A + "name", component.tag)
        schemes = {d.get(A + "scheme") for d in intent_filter.findall("data") if d.get(A + "scheme") is not None}
        for scheme in sorted(schemes - {"https"}):
            print(f"{name}: an intent filter declares the scheme '{scheme}' (only https is allowed)")
        actions = {a.get(A + "name") for a in intent_filter.findall("action")}
        categories = {c.get(A + "name") for c in intent_filter.findall("category")}
        browsable_view = ("android.intent.action.VIEW" in actions
                          and "android.intent.category.BROWSABLE" in categories)
        if browsable_view and "https" in schemes and intent_filter.get(A + "autoVerify") != "true":
            print(f"{name}: a browsable https filter without android:autoVerify=\"true\" (an unverified App Link)")
PY
}

FAIL=0
check() {
    out=$(violations "$1")
    rc=$?
    if [ $rc -ne 0 ] || [ -n "$out" ]; then
        FAIL=1
        echo ""
        echo "FAIL deep-links ($2): $1"
        echo "$out" | sed 's/^/    /'
        echo "  §8.12: only verified App Links; a custom scheme can be claimed by any app (task 0011)."
    fi
}

check "$SRC_MANIFEST" "source"

# The merged manifests: every variant that was built, not just the first one found.
MERGED=$(find androidApp/build -path '*merged_manifest*' -name 'AndroidManifest.xml' 2>/dev/null)
if [ -z "$MERGED" ]; then
    if [ "${LUME_REQUIRE_MERGED_MANIFEST:-0}" = "1" ]; then
        echo "FAIL deep-links: no MERGED manifest exists, and this run requires it."
        echo "  Run an Android build first: the merged half is the one that sees what dependencies add."
        exit 1
    fi
    [ $FAIL -eq 0 ] || exit 1
    echo "deep-links: source manifest OK — MERGED manifest NOT CHECKED (no build yet); set LUME_REQUIRE_MERGED_MANIFEST=1 to make this a failure."
    exit 0
fi
for m in $MERGED; do check "$m" "merged"; done

[ $FAIL -eq 0 ] || exit 1
echo "deep-links: OK (source and $(echo "$MERGED" | wc -l | tr -d ' ') merged manifest(s): https only, App Links verified)"
