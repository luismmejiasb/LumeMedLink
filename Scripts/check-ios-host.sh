#!/bin/sh
# iOS host gate (ADR-0025) — the Swift, the Info.plist and the entitlements of `iosApp/`.
#
# WHY THIS FILE EXISTS AT ALL: every other gate scans `composeApp/src androidApp/src`. The moment
# the iOS host landed, it was a directory full of security-relevant decisions that NO gate could
# see — a blind spot created by the same change that created the host. This closes it in the same
# commit rather than filing it.
#
# The host is small on purpose, and everything in it is a decision the constitution names:
#   PRESENCE  the third-party keyboard veto (§8.10, the one asymmetry in iOS's favour) and the
#             privacy cover on willResignActive (ADR-0010 / mirror of ADR-0028 of LumeMed).
#   ABSENCE   ATS exceptions (§7), file sharing and document delivery (ADR-0007), custom URL
#             schemes (§8.12), a SHARED keychain access group (ADR-0001 — LumeMed is signed by the
#             same team and holds the record), clipboard, ad-hoc networking, and free-text logging.
#
# Rehearsed against bait before being trusted.
#
# Usage: Scripts/check-ios-host.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

SWIFT="iosApp/iosApp"
PLIST="iosApp/iosApp/Info.plist"
ENTITLEMENTS="iosApp/iosApp/iosApp.entitlements"
PROJECT="iosApp/iosApp.xcodeproj/project.pbxproj"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# Swift comments never count as the mechanism — the same hole that FLAG_SECURE's gate was caught by
# three times (a KDoc naming the API passed a plain grep). The line-based filter this replaces only
# recognised a comment whose LINE STARTS with a marker, so the inner lines of a `/* … */` block
# walked straight through it (ADR-0029). A tokenizer blanks them, nested blocks included.
#
# Two helpers on purpose. PRESENCE also drops string literals, because a token inside a string is
# never the call. ABSENCE keeps them: a forbidden API named in a string is still worth a look, and
# hiding it would be the gate helping the bait.
SWIFT_FILES=$(find $SWIFT -name '*.swift' 2>/dev/null)
swift_code() {
    [ -n "$SWIFT_FILES" ] || return 0
    # shellcheck disable=SC2086
    python3 Scripts/lib/uncomment.py --lang c $SWIFT_FILES 2>/dev/null | grep -nE "$1"
}
swift_has() {
    [ -n "$SWIFT_FILES" ] || return 1
    # shellcheck disable=SC2086
    python3 Scripts/lib/uncomment.py --lang c --strip-strings --flatten $SWIFT_FILES 2>/dev/null |
        grep -qE "$1"
}

# ── The files must exist. A gate that silently passes on a missing host is worse than none ───────
for f in "$PLIST" "$ENTITLEMENTS" "$PROJECT"; do
    if [ ! -f "$f" ]; then
        fail "ios-host: $f is missing" "The host's security decisions live in these three files; one absent means the gate below proved nothing."
    fi
done
if [ $FAIL -ne 0 ]; then exit 1; fi

# ── PRESENCE ────────────────────────────────────────────────────────────────────────────────────
# The REFUSAL, not the method's name. Bait caught this one: leaving the delegate method in place
# and changing its body to `return true` allows every third-party keyboard, and a scan for the
# identifier stayed green — the gate asserted that the hook EXISTS, not that it refuses (ADR-0029).
if ! swift_has 'shouldAllowExtensionPointIdentifier'; then
    fail "ios-host: third-party keyboard veto missing" \
         "iOS CAN refuse custom keyboards app-wide and Android cannot (§8.10). Dropping it here silently gives up the one place this app is better protected than its Android half."
elif ! swift_has 'extensionPointIdentifier[[:space:]]*!=[[:space:]]*\.keyboard'; then
    fail "ios-host: the keyboard veto hook exists but does not refuse .keyboard" \
         "The delegate method must return false for .keyboard (§8.10). A hook that answers true for everything is the default with extra steps."
fi
if ! swift_has 'applicationWillResignActive'; then
    fail "ios-host: privacy cover not armed on willResignActive" \
         "iOS snapshots the screen when the app leaves the foreground and willResignActive is the last moment BEFORE that (ADR-0010). didEnterBackground is already too late."
fi
if ! swift_has 'windowLevel'; then
    fail "ios-host: privacy cover is not in its own window" \
         "A cover inside the app's window can be covered, reordered or removed by whatever is presented on top. Its whole point is to sit above all of that (ADR-0025)."
fi
# The build phase must pin the Kotlin framework build type, and this is the highest-value
# assertion in this file because its absence FAILS SILENTLY. Without KOTLIN_FRAMEWORK_BUILD_TYPE
# the Kotlin Gradle plugin prints "Unable to detect Kotlin framework build type" as a WARNING,
# does not refresh build/xcode-frameworks, and the linker then uses whatever framework was left
# there. The build stays green while the app runs OLD Kotlin. That is not hypothetical: it is what
# this host did between 2026-08-25 and 2026-09-07, and it invalidated every iOS device observation
# made in that window — including a live control that "proved" something by patching Kotlin that
# never reached the binary (ADR-0028, bitácora 0026).
# The ASSIGNMENT, not the name: this gate's own explanation of the setting lives inside the build
# phase, and a plain token scan was satisfied by that comment (caught by bait).
if ! grep -q 'KOTLIN_FRAMEWORK_BUILD_TYPE=' "$PROJECT"; then
    fail "ios-host: the build phase does not pin KOTLIN_FRAMEWORK_BUILD_TYPE" \
         "Without it the Kotlin framework is not refreshed and the app links STALE code while the build stays green (ADR-0028). Every iOS measurement becomes worthless without announcing itself."
fi
if ! grep -q 'CODE_SIGN_ENTITLEMENTS' "$PROJECT"; then
    fail "ios-host: the project does not reference the entitlements file" \
         "Without entitlements the app belongs to no keychain group and every SecItem call answers -34018. The file existing is not the control; the project pointing at it is."
fi

# ── ABSENCE, in the Info.plist ──────────────────────────────────────────────────────────────────
# XML comments are stripped first: this plist EXPLAINS each forbidden key by name, and a plain grep
# would fail on its own documentation. (Caught here exactly as it was caught in F12's NSC.)
plist_code=$(sed 's/<!--.*-->//g' "$PLIST" | awk 'BEGIN{c=0} /<!--/{c=1} !c{print} /-->/{c=0}')
plist_has() { echo "$plist_code" | grep -q "$1"; }

if plist_has 'NSAppTransportSecurity'; then
    fail "ios-host: ATS key present in Info.plist" \
         "ATS is secure by default and every key under it is a weakening (§7). The control is that there is nothing to read." \
         "$(echo "$plist_code" | grep -n 'NSAppTransportSecurity')"
fi
if plist_has 'UIFileSharingEnabled' || plist_has 'LSSupportsOpeningDocumentsInPlace'; then
    fail "ios-host: the app container is exposed to Files.app" \
         "Either key turns this app's container into a share point. This app never hands a document to anyone (ADR-0007, F19)."
fi
if plist_has 'CFBundleURLTypes'; then
    fail "ios-host: custom URL scheme declared" \
         "Any app can claim a custom scheme (§8.12). Deep links arrive as verified universal links or not at all."
fi

# ── ABSENCE, in the entitlements ────────────────────────────────────────────────────────────────
# The keychain group must be the app's own and nothing else. A shared group would let any app signed
# by the same team read this app's session tokens — and LumeMed, which holds the clinical record, is
# signed by the same team. That is the boundary this whole repo exists for (ADR-0001).
groups=$(sed 's/<!--.*-->//g' "$ENTITLEMENTS" | awk 'BEGIN{c=0} /<!--/{c=1} !c{print} /-->/{c=0}' \
    | grep -A20 'keychain-access-groups' | grep '<string>' || true)
if [ -n "$groups" ]; then
    bad=$(echo "$groups" | grep -v 'AppIdentifierPrefix)$(CFBundleIdentifier)' || true)
    if [ -n "$bad" ]; then
        fail "ios-host: a keychain access group other than this app's own" \
             "A shared group is readable by every app signed by the same team, LumeMed included (ADR-0001)." "$bad"
    fi
fi

# ── ABSENCE, in the Swift ───────────────────────────────────────────────────────────────────────
hits=$(swift_code 'UIPasteboard')
[ -n "$hits" ] && fail "ios-host: clipboard API in the host" \
    "Datos personales do not go to the shared pasteboard (§8.9)." "$hits"

hits=$(swift_code 'URLSession|NSURLConnection|CFNetwork')
[ -n "$hits" ] && fail "ios-host: networking in the host" \
    "Every byte leaves through core/networking's hardened stack (§7). The host hosts; it does not call." "$hits"

hits=$(swift_code 'UIActivityViewController|UIDocumentInteractionController|UIPrintInteractionController|UIDocumentPickerViewController')
[ -n "$hits" ] && fail "ios-host: a document delivery surface in the host" \
    "This app shows, sends and prints no clinical document, by any route (ADR-0007, F19)." "$hits"

hits=$(swift_code 'print\(|NSLog\(|os_log\(|Logger\(')
[ -n "$hits" ] && fail "ios-host: free-text logging in the host" \
    "There is ONE logging path with a closed vocabulary (§8.1, ADR-0020). Swift's print writes whatever it is handed." "$hits"

if [ $FAIL -eq 0 ]; then
    echo "ios-host: OK"
else
    exit 1
fi
