#!/bin/sh
# Firebase posture gate (ADR-0052).
#
# The author allowed Firebase on 2026-10-13 and put four conditions on it. Three of them are code, and code that is
# not asserted drifts: the case that made the conditions necessary (the FTC against GoodRx, 2023) was not an SDK flaw
# but what an app put in its events. So this gate asserts, where they run:
#
#   1. Everything is OFF from the factory, on both platforms: the Android manifest (parsed) and the iOS Info.plist
#      (parsed). Crashlytics and Analytics turn on only through the person's consent, at runtime (§2).
#   2. Nothing collects before the app says so: FirebaseInitProvider and the ad-identifier, ad-services and
#      install-referrer permissions are REMOVED from the merge (§1, §8). Asserted on the source manifest, because the
#      merged half only exists after a build and the rehearsal runs without one.
#   3. Analytics speaks only the catalog: one call site per platform, a catalog name, and no parameters (§3).
#   4. No identity and no free text reaches Firebase: no user ID, user properties, custom keys, breadcrumb logs, default
#      parameters or recordException — anywhere in the app or the host (§3, §4).
#   5. A crash travels without its message: the Android adapter installs the message-free handler, and the iOS host
#      records an empty reason (§4).
#
# Comments are stripped by tokenizer and XML/plists are parsed, never filtered by line (ADR-0029). Strings are stripped
# where a literal could only hide a call, and kept where the literal IS the assertion (the empty reason).
#
# Usage: Scripts/check-firebase-posture.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

MANIFEST="androidApp/src/main/AndroidManifest.xml"
PLIST="iosApp/iosApp/Info.plist"
ANDROID_ADAPTER="composeApp/src/androidMain/kotlin/com/luismejias/lumemedlink/core/telemetry/Telemetry.android.kt"
IOS_HOST="iosApp/iosApp/FirebaseHost.swift"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

for f in "$MANIFEST" "$PLIST" "$ANDROID_ADAPTER" "$IOS_HOST"; do
    [ -f "$f" ] || fail "firebase: $f is missing" "The gate asserts a control that lives in it (ADR-0052)."
done
[ $FAIL -eq 0 ] || exit 1

# ── 1 + 2 · Android: off from the factory, and nothing merged that would read an identifier ───────
android_problems=$(python3 - "$MANIFEST" <<'PY'
import sys, xml.etree.ElementTree as ET
A = "{http://schemas.android.com/apk/res/android}"
T = "{http://schemas.android.com/tools}"
root = ET.parse(sys.argv[1]).getroot()
app = root.find("application")
problems = []
meta = {m.get(A + "name"): m.get(A + "value") for m in app.findall("meta-data")}
for name in ("firebase_crashlytics_collection_enabled", "firebase_analytics_collection_enabled",
             "google_analytics_adid_collection_enabled", "google_analytics_ssaid_collection_enabled",
             "google_analytics_default_allow_ad_personalization_signals",
             "google_analytics_automatic_screen_reporting_enabled", "firebase_messaging_auto_init_enabled",
             "delivery_metrics_exported_to_big_query_enabled"):
    if meta.get(name) != "false":
        problems.append(f'<meta-data {name}> must be "false" on <application> (it is {meta.get(name)!r})')
removed = {p.get(A + "name") for p in root.findall("uses-permission") if p.get(T + "node") == "remove"}
for perm in ("com.google.android.gms.permission.AD_ID", "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
             "android.permission.ACCESS_ADSERVICES_AD_ID",
             "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE"):
    if perm not in removed:
        problems.append(f'{perm} is not removed from the merge (tools:node="remove")')
providers = {p.get(A + "name"): p.get(T + "node") for p in app.findall("provider")}
if providers.get("com.google.firebase.provider.FirebaseInitProvider") != "remove":
    problems.append('FirebaseInitProvider is not removed: Firebase would start at process launch, before the app decides')
print("\n".join(problems))
PY
)
[ -n "$android_problems" ] && fail "firebase: the Android manifest does not keep Firebase off from the factory" \
    "ADR-0052 §1, §2, §8 — off until the person consents; no ad ID, no attribution, no start at launch." "$android_problems"

# ── 1 · iOS: the same, in the Info.plist ─────────────────────────────────────────────────────────
ios_problems=$(python3 - "$PLIST" <<'PY'
import plistlib, sys
plist = plistlib.load(open(sys.argv[1], "rb"))
problems = []
for key in ("FirebaseCrashlyticsCollectionEnabled", "FIREBASE_ANALYTICS_COLLECTION_ENABLED",
            "GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED", "GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS",
            "GOOGLE_ANALYTICS_REGISTRATION_WITH_AD_NETWORK_ENABLED", "FirebaseAutomaticScreenReportingEnabled",
            "FirebaseMessagingAutoInitEnabled", "FirebaseAppDelegateProxyEnabled"):
    if plist.get(key) is not False:
        problems.append(f"{key} must be <false/> (it is {plist.get(key)!r})")
print("\n".join(problems))
PY
)
[ -n "$ios_problems" ] && fail "firebase: the Info.plist does not keep Firebase off from the factory" \
    "ADR-0052 §1, §2 — the same posture as Android, read the way iOS reads it." "$ios_problems"

# ── 3 + 4 · What reaches Firebase, in every source file that ships ───────────────────────────────
KOTLIN=$(find composeApp/src -path '*Test*' -prune -o -path '*/build/*' -prune -o -name '*.kt' -print 2>/dev/null)
SWIFT=$(find iosApp -path '*/build/*' -prune -o -name '*.swift' -print 2>/dev/null)
code_of() { python3 Scripts/lib/uncomment.py --lang c --strip-strings --flatten "$1" 2>/dev/null; }

# Firebase Analytics' call: one place per platform, a catalog name, no parameters. On Android the call is reachable
# only from a file that names the package — by import or fully qualified — so: one such file, and inside it every
# logEvent is the catalog's. (The app's own lambda is also called logEvent; it takes an AnalyticsEvent and nothing else.)
calls=$(for f in $KOTLIN; do
    code_of "$f" | grep -q 'firebase\.analytics' && [ "$f" != "$ANDROID_ADAPTER" ] && echo "$f names com.google.firebase.analytics"
done)
# One level of nested parentheses, so the call's own argument list is read whole.
adapter_calls=$(code_of "$ANDROID_ADAPTER" | grep -oE '\.logEvent\([^()]*(\([^()]*\))?[^()]*\)' | sort -u)
# Details are allowed since ADR-0053, and only one way: a Bundle built from analyticsValues(), enum names, never data.
[ "$adapter_calls" = '.logEvent(event.eventName, bundleOf(details))' ] ||
    calls="$calls
the Android adapter logs something other than a catalog name with its closed details: ${adapter_calls:-no call at all}"
code_of "$ANDROID_ADAPTER" | grep -qE 'Bundle\? = analyticsValues\(details\)' ||
    calls="$calls
the Android adapter builds its Bundle from something other than analyticsValues(details)"
IOS_BRIDGE="composeApp/src/iosMain/kotlin/com/luismejias/lumemedlink/core/telemetry/Telemetry.ios.kt"
code_of "$IOS_BRIDGE" | grep -qE 'bridge\.logEvent\(event\.eventName, analyticsValues\(details\)\)' ||
    calls="$calls
the iOS bridge hands Swift something other than analyticsValues(details)"
[ -n "$(echo "$calls" | tr -d '[:space:]')" ] && fail "firebase: Analytics is called outside the catalog on Android" \
    "ADR-0052 §3: only the adapter reaches Firebase Analytics, with a catalog name and its closed details (ADR-0053). Free events are GoodRx's mechanism." "$calls"

swift_calls=$(for f in $SWIFT; do
    code_of "$f" | grep -oE 'Analytics\.logEvent\([^)]*\)' | sed "s|^|$f: |"
done)
IOS_CALL="$IOS_HOST: Analytics.logEvent(name, parameters: parameters.isEmpty ? nil : parameters)"
bad_swift=$(echo "$swift_calls" | grep -v '^$' | grep -vxF "$IOS_CALL" || true)
echo "$swift_calls" | grep -qxF "$IOS_CALL" ||
    bad_swift="$bad_swift
the iOS host no longer logs the catalog name with only the details Kotlin reduced"
[ -n "$(echo "$bad_swift" | tr -d '[:space:]')" ] && fail "firebase: Analytics is called outside the catalog on iOS" \
    "ADR-0052 §3, ADR-0053: FirebaseHost.swift carries the Kotlin catalog's name and the details Kotlin reduced to enum names." "$bad_swift"

# Identity and free text: forbidden everywhere, in both languages.
# initiateOnDeviceConversion*: Analytics' API to hash and send an email or a phone number for ad conversion.
FORBIDDEN='initiateOnDeviceConversion|setUserId|setUserID|setUserProperty|setCustomKey|setCustomKeys|setCustomValue|setDefaultEventParameters|recordException|record\(error|crashlytics\(\)\.log|crashlytics\.log|setConsent'
hits=$(for f in $KOTLIN $SWIFT; do
    code_of "$f" | grep -noE "$FORBIDDEN" | sed "s|^|$f:|"
done)
[ -n "$hits" ] && fail "firebase: an identity or free text could reach Firebase" \
    "ADR-0052 §3–4: no user ID, no user properties, no custom keys, no breadcrumbs, no recorded exceptions with their messages." "$hits"

# ── 5 · A crash travels without its message ──────────────────────────────────────────────────────
code_of "$ANDROID_ADAPTER" | grep -qE 'installMessageFreeCrashes\(\)' ||
    fail "firebase: the Android adapter does not install the message-free crash handler" \
         "ADR-0052 §4: Crashlytics must receive the copy without messages, never the original throwable." "$ANDROID_ADAPTER"
# Strings KEPT here: the empty literal is the assertion.
host=$(python3 Scripts/lib/uncomment.py --lang c --flatten "$IOS_HOST" 2>/dev/null)
models=$(printf '%s\n' "$host" | grep -oE 'ExceptionModel\([^)]*\)' || true)
[ "$models" = 'ExceptionModel(name: typeName, reason: "")' ] ||
    fail "firebase: the iOS host records a crash with a reason" \
         "ADR-0052 §4: one ExceptionModel, named by the type, with an empty reason." "${models:-no ExceptionModel at all}"

if [ $FAIL -eq 0 ]; then
    echo "firebase-posture: OK"
else
    exit 1
fi
