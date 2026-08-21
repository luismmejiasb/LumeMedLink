#!/bin/sh
# Network transport posture gate (F12, ADR-0016).
#
# THIS GATE READS THE **MERGED** MANIFEST, and that is its whole point. Every other gate in this
# repo reads androidApp/src/main/AndroidManifest.xml — the file we write — and is therefore blind
# to what dependencies merge into the app. That vector is not hypothetical here: okhttp-android
# injects android.permission.INTERNET and androidx.biometric injects the deprecated
# USE_FINGERPRINT, neither of which appears in any source file of this repo (verified in the
# manifest-merger blame report). A library could just as easily merge usesCleartextTraffic="true".
#
# So: source assertions catch OUR regressions, merged assertions catch THEIRS. Both are needed.
#
# Rehearsed against bait before being trusted.
#
# Usage: Scripts/check-network-posture.sh   (a build must have produced a merged manifest)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

SRC_MANIFEST="androidApp/src/main/AndroidManifest.xml"
NSC="androidApp/src/main/res/xml/network_security_config.xml"
SRC="composeApp/src androidApp/src"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# Every permission this app is allowed to ship with. A permission arriving by merge from a
# dependency is still a permission the app requests, so it must be listed here consciously.
ALLOWED_PERMISSIONS="android.permission.INTERNET
android.permission.USE_BIOMETRIC
android.permission.USE_FINGERPRINT"

# ── 1. Source manifest: our own declarations ────────────────────────────────────────────────────
attr_present() { grep -E "$1" "$SRC_MANIFEST" 2>/dev/null | grep -v '<!--' | grep -q 'android:'; }

[ -f "$SRC_MANIFEST" ] || { echo "FAIL manifest missing"; exit 1; }
attr_present 'android:usesCleartextTraffic="false"' ||
    fail "network: usesCleartextTraffic is not declared false" \
         "On API 26/27 devices the platform default permits cleartext; declaring it makes the posture the same everywhere (ADR-0016)."
attr_present 'android:networkSecurityConfig=' ||
    fail "network: no networkSecurityConfig resource is declared" \
         "It is the only place trust anchors can be pinned to system-only (ADR-0016)."

# ── 2. The config resource itself ───────────────────────────────────────────────────────────────
# XML comments are stripped before any assertion. This file's own comment explains what
# <debug-overrides> and src="user" would do, and a naive grep read the explanation as the
# configuration — the third time in this repo that a comment fooled a gate. Strip, then grep.
nsc_code() {
    python3 - "$NSC" <<'PY'
import re, sys, pathlib
print(re.sub(r'<!--.*?-->', '', pathlib.Path(sys.argv[1]).read_text(), flags=re.S))
PY
}

if [ ! -f "$NSC" ]; then
    fail "network: $NSC is missing" "The manifest points at a resource that does not exist."
else
    NSC_CODE=$(nsc_code)
    echo "$NSC_CODE" | grep -q 'cleartextTrafficPermitted="false"' ||
        fail "network: the config does not deny cleartext" "base-config must set cleartextTrafficPermitted=\"false\"."
    echo "$NSC_CODE" | grep -q '<certificates src="system"' ||
        fail "network: the config does not pin system trust anchors" "Without it the default anchor set applies (ADR-0016)."
    if echo "$NSC_CODE" | grep -q '<certificates src="user"'; then
        fail "network: user-installed CAs are trusted" \
             "An employer's or an attacker's CA could then intercept this app's TLS (ADR-0016)."
    fi
    if echo "$NSC_CODE" | grep -q 'cleartextTrafficPermitted="true"'; then
        fail "network: some scope permits cleartext" "No scope may (ADR-0016)." "$(echo "$NSC_CODE" | grep -n 'cleartextTrafficPermitted="true"')"
    fi
    if echo "$NSC_CODE" | grep -q '<debug-overrides>'; then
        fail "network: <debug-overrides> present" \
             "It makes debug builds trust user CAs — a deliberate change to ADR-0016, never a side effect."
    fi
fi

# The platform AUTO-DISCOVERS a sibling <name>_debug.xml in debuggable builds, even though nothing
# references it. Its mere existence would change the posture invisibly.
if [ -f "androidApp/src/main/res/xml/network_security_config_debug.xml" ]; then
    fail "network: an auto-discovered _debug config exists" \
         "Debuggable builds would silently load it instead (ADR-0016)."
fi

# ── 3. The MERGED manifest: what dependencies put in the shipped app ────────────────────────────
MERGED=$(find androidApp/build -path '*merged_manifest*' -name 'AndroidManifest.xml' 2>/dev/null | head -1)
if [ -z "$MERGED" ]; then
    echo "network-posture: source checks OK — MERGED manifest not built yet, run a build to check the dependency vector."
    [ $FAIL -eq 0 ] && exit 0 || exit 1
fi

if grep -q 'android:usesCleartextTraffic="true"' "$MERGED"; then
    fail "network: the MERGED manifest permits cleartext" \
         "A dependency re-enabled it; our source manifest says otherwise. Inspect the merge blame report." "$MERGED"
fi
grep -q 'android:networkSecurityConfig=' "$MERGED" ||
    fail "network: the merged manifest lost the networkSecurityConfig reference" "Check manifest merging."

merged_perms=$(grep -oE 'android:name="android\.permission\.[A-Z_]+"' "$MERGED" | sed 's/android:name="//;s/"//' | sort -u)
for perm in $merged_perms; do
    echo "$ALLOWED_PERMISSIONS" | grep -qx "$perm" || {
        blame=$(find androidApp/build -name 'manifest-merger-blame*' 2>/dev/null | head -1)
        origin=""
        [ -n "$blame" ] && origin=$(grep -A1 "$perm" "$blame" | grep -oE '\[[^]]+\]' | head -1)
        fail "network: the app ships an undeclared permission: $perm ${origin}" \
             "A permission merged in by a dependency is still one the app requests. Decide it, then add it to ALLOWED_PERMISSIONS."
    }
done

if [ $FAIL -eq 0 ]; then
    echo "network-posture: OK (merged manifest checked, $(echo "$merged_perms" | wc -l | tr -d ' ') permissions)"
else
    exit 1
fi
