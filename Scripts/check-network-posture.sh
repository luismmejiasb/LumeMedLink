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
#
# The last entry is the app's OWN signature-level permission, which androidx.core declares and uses
# to keep dynamically registered receivers unexported: it grants nothing to any other app. Listed
# because it is in the merge, and an unlisted permission is a permission nobody decided.
#
# The four after it are what firebase-messaging and its closure merge in (ADR-0038, read off their AAR
# manifests on 2026-10-07): decided by that ADR so the push slice does not arrive to a gate that has
# to be loosened in the same change. None is in the merge today.
ALLOWED_PERMISSIONS="android.permission.INTERNET
android.permission.USE_BIOMETRIC
android.permission.USE_FINGERPRINT
com.luismejias.lumemedlink.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
android.permission.ACCESS_NETWORK_STATE
android.permission.WAKE_LOCK
android.permission.POST_NOTIFICATIONS
com.google.android.c2dm.permission.RECEIVE"

# ── 1. Source manifest: our own declarations ────────────────────────────────────────────────────
# PARSED, not grepped, and read off the <application> ELEMENT. The line-based filter this replaces
# (`grep -v '<!--'`) was walked through by its own class of bait: an XML comment spanning four lines
# carries `<!--` only on its first line, so the four posture attributes were moved inside one, the
# file stayed well-formed, the element lost all four, and this gate stayed green. A parser cannot
# see a comment, and it cannot mistake `android:usesCleartextTraffic` on an <activity> for the
# application's posture either (ADR-0029).
app_attr() { python3 Scripts/lib/xmlattr.py "$SRC_MANIFEST" application "$1" 2>/dev/null; }

[ -f "$SRC_MANIFEST" ] || { echo "FAIL manifest missing"; exit 1; }
[ "$(app_attr 'android:usesCleartextTraffic')" = "false" ] ||
    fail "network: usesCleartextTraffic is not declared false on <application>" \
         "On API 26/27 devices the platform default permits cleartext; declaring it makes the posture the same everywhere (ADR-0016)."
[ -n "$(app_attr 'android:networkSecurityConfig')" ] ||
    fail "network: no networkSecurityConfig resource is declared on <application>" \
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

# ── 3. iOS: the NSURLSession posture (F12; ADR-0016, amended 2026-10-07; task 0004) ─────────────
# Until 2026-10-07 nothing gated this half: the line that keeps the bearer token out of
# Library/Caches had no test and no gate, though its KDoc offered itself as a seam for both.
#
# Asserted INSIDE the functions production runs, with comments and literals blanked (ADR-0029):
# platformHttpEngine() builds lumeDarwinEngine() with no addition, lumeDarwinEngine registers the
# posture, and the posture switches each shared store off — once, with no later call of the same
# setter to override it. The same words anywhere else in the file (a helper nobody calls, a comment,
# a string) are not the control. Whether KTOR still lets the posture have the last word is not a grep
# question: DarwinSessionPostureTest measures it on the live session the real engine creates.
IOS_ENGINE="composeApp/src/iosMain/kotlin/com/luismejias/lumemedlink/core/networking/PlatformHttpEngine.ios.kt"
kfun() { python3 Scripts/lib/kfun.py "$IOS_ENGINE" "$1" 2>/dev/null; }
occurrences() { printf '%s' "$1" | grep -oF "$2" | wc -l | tr -d ' '; }
if [ ! -f "$IOS_ENGINE" ]; then
    fail "network (iOS): the engine file is missing" "$IOS_ENGINE"
else
    [ "$(kfun platformHttpEngine)" = "lumeDarwinEngine()" ] ||
        fail "network (iOS): platformHttpEngine() does not build lumeDarwinEngine() with no addition" \
             "Production must build the engine the posture test measures, and must add nothing to it."
    kfun lumeDarwinEngine | grep -qF 'configureSession { applyLumeSessionPosture() }' ||
        fail "network (iOS): the engine does not register the session posture" \
             "lumeDarwinEngine must call configureSession { applyLumeSessionPosture() } (ADR-0016)."
    POSTURE=$(kfun applyLumeSessionPosture)
    for call in 'setURLCache(null)' 'setURLCredentialStorage(null)' 'setHTTPCookieStorage(null)' \
        'setHTTPShouldSetCookies(false)'; do
        setter="${call%%(*}("
        if [ "$(occurrences "$POSTURE" "$call")" -lt 1 ] ||
            [ "$(occurrences "$POSTURE" "$setter")" -ne "$(occurrences "$POSTURE" "$call")" ]; then
            fail "network (iOS): the session posture does not set $call, or sets it more than one way" \
                 "applyLumeSessionPosture must switch each shared store off, and nothing after may switch it back (ADR-0016, task 0004)."
        fi
    done
fi

# ── 4. The MERGED manifest: what dependencies put in the shipped app ────────────────────────────
# EVERY merged variant, not the first one found (task 0002, F08). Read as a list, newline-separated.
MERGED_ALL=$(find androidApp/build -path '*merged_manifest*' -name 'AndroidManifest.xml' 2>/dev/null)
MERGED=$(printf '%s\n' "$MERGED_ALL" | head -1)
if [ -z "$MERGED" ]; then
    # THE HALF THAT NEVER RAN. This skip is fine on a developer machine before a build — and in CI
    # it made the assertion that gives this gate its whole point a no-op that printed OK. The gates
    # job runs on ubuntu with no build, so the merged manifest was never there; the build job never
    # ran the gates. From 2026-08-21 to 2026-09-21 nothing anywhere checked what dependencies merge
    # into the shipped manifest, and the line above said "OK" every time (audit, ADR-0029).
    #
    # So CI sets LUME_REQUIRE_MERGED_MANIFEST=1 and the skip becomes a failure there. A gate is
    # allowed to be unable to check something; it is not allowed to report OK while doing so.
    if [ "${LUME_REQUIRE_MERGED_MANIFEST:-0}" = "1" ]; then
        fail "network: the MERGED manifest does not exist, and this run requires it" \
             "Run an Android build before this gate. The merged half is the only one that sees what dependencies inject (okhttp brings INTERNET, androidx.biometric brings USE_FINGERPRINT)."
        exit 1
    fi
    echo "network-posture: source checks OK — MERGED manifest NOT CHECKED (no build yet). This half is the one that sees dependency-injected permissions; run a build, or set LUME_REQUIRE_MERGED_MANIFEST=1 to make this a failure."
    [ $FAIL -eq 0 ] && exit 0 || exit 1
fi

for m in $MERGED_ALL; do
if grep -q 'android:usesCleartextTraffic="true"' "$m"; then
    fail "network: the MERGED manifest permits cleartext" \
         "A dependency re-enabled it; our source manifest says otherwise. Inspect the merge blame report." "$m"
fi
grep -q 'android:networkSecurityConfig=' "$m" ||
    fail "network: the merged manifest lost the networkSecurityConfig reference" "Check manifest merging." "$m"
done

# PARSED, every uses-permission and uses-permission-sdk-23, by FULL name (task 0002, F08). The regex
# this replaces only saw `android.permission.*`: `com.google.android.gms.permission.AD_ID` or FCM's
# `c2dm` permission would have shipped with this gate green (reproduced), and today's merge already
# carries one it never reported — androidx.core's own signature permission, below.
merged_perms=$(for m in $MERGED_ALL; do python3 - "$m" <<'PY'
import sys
import xml.etree.ElementTree as ET
A = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(sys.argv[1]).getroot()
for tag in ("uses-permission", "uses-permission-sdk-23"):
    for node in root.iter(tag):
        print(node.get(A + "name"))
PY
done | sort -u)
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
    echo "network-posture: OK ($(printf '%s\n' "$MERGED_ALL" | wc -l | tr -d ' ') merged manifests checked, $(echo "$merged_perms" | wc -l | tr -d ' ') permissions)"
else
    exit 1
fi
