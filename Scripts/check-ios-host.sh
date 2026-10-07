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
#             privacy cover on the SCENE's willDeactivate (ADR-0031 — the app-delegate method this line
#             used to name is never called in a scene app, and the body below forbids it).
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
# THIS ASSERTION USED TO ENFORCE DEAD CODE. It required `applicationWillResignActive`, and in a
# SwiftUI app — which adopts the UIScene lifecycle — UIKit NEVER CALLS that method. The gate was
# green over a cover that had not armed once since the host was written (ADR-0031, measured with a
# positive control in the same delegate).
#
# So the assertion is inverted AND moved to the mechanism that actually fires: the scene
# notification. The four methods scenes replace are named here so that re-introducing any of them
# is a decision, not a habit.
if ! swift_has 'UIScene\.willDeactivateNotification'; then
    fail "ios-host: privacy cover not armed on the scene's willDeactivate" \
         "iOS snapshots the screen when the app leaves the foreground and willDeactivate is the last moment BEFORE that (ADR-0010/0031). didEnterBackground is already too late, and the app-delegate methods are never called in a scene-based app."
fi
if ! swift_has 'UIScene\.didActivateNotification'; then
    fail "ios-host: the privacy cover is never taken down" \
         "A cover that never hides is not a control, it is a blank app (ADR-0031)."
fi
for dead in applicationWillResignActive applicationDidBecomeActive applicationDidEnterBackground applicationWillEnterForeground; do
    hits=$(swift_code "$dead")
    if [ -n "$hits" ]; then
        fail "ios-host: $dead is implemented and will never be called" \
             "This app adopts the UIScene lifecycle (SwiftUI App + WindowGroup), and UIKit does not call those four methods. Code there is dead and reads as a control (ADR-0031). Use the UIScene notifications." \
             "$hits"
    fi
done
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

# And the SECOND half of the same staleness, which the first fix did not touch (ADR-0030).
# KOTLIN_FRAMEWORK_BUILD_TYPE makes GRADLE's output fresh. It does nothing about the LINK: the
# Kotlin framework is static and arrives through `OTHER_LDFLAGS -framework`, so no input Xcode
# tracks changes when Kotlin changes, and a Kotlin-only edit leaves the app binary byte-identical.
# Measured, with a control: same sha256, same mtime, old code, build green.
#
# The ASSIGNMENT and the CALL, not the words: the build phase must compare the framework against a
# stamp AND delete the linked product when it moved. Declaring the framework in the Frameworks
# build phase was tried first and measured NOT to work — the entry in BUILT_PRODUCTS_DIR is a
# symlink whose own mtime never moves — so asserting a file reference here would assert a control
# that does nothing.
#
# This gate only proves the mechanism is WRITTEN. That it WORKS is
# Scripts/verify-ios-link-freshness.sh, which runs the two-build experiment with a live control.
if ! grep -q 'kotlin-framework.stamp' "$PROJECT"; then
    fail "ios-host: the build phase does not stamp the Kotlin framework" \
         "Without a stamp there is nothing to compare, so nothing can notice the framework moved (ADR-0030)."
fi
if ! grep -q 'rm -f .*TARGET_BUILD_DIR/\$EXECUTABLE_PATH' "$PROJECT"; then
    fail "ios-host: the build phase does not force a relink when the framework changed" \
         "Deleting the linked product is what makes Xcode link again; nothing else in this project points at the framework (ADR-0030)."
fi
if ! grep -q 'CODE_SIGN_ENTITLEMENTS' "$PROJECT"; then
    fail "ios-host: the project does not reference the entitlements file" \
         "Without entitlements the app belongs to no keychain group and every SecItem call answers -34018. The file existing is not the control; the project pointing at it is."
fi

# ── The Info.plist and the entitlements, PARSED (ADR-0029 decision 4; task 0002, F02/F04) ──────
# These two used to be read with sed and awk, which drop the line on which a comment OPENS: a
# forbidden key with a trailing comment after it — ATS switched off, a shared keychain group listed
# first — kept this gate green (reproduced). plistlib parses what iOS will read, a comment is
# invisible to it, and it runs on the ubuntu job, which has no plutil.
plist_problems() {
    python3 - "$PLIST" "$ENTITLEMENTS" composeApp/src/iosMain <<'PY'
import pathlib, plistlib, re, sys
plist_path, ent_path, ios_main = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
try:
    info = plistlib.loads(pathlib.Path(plist_path).read_bytes())
    ent = plistlib.loads(pathlib.Path(ent_path).read_bytes())
except Exception as error:  # a file iOS cannot read is never a pass
    print(f"cannot parse: {error}")
    sys.exit(0)

forbidden = {
    "NSAppTransportSecurity": "ATS is secure by default and every key under it is a weakening (§7)",
    "UIFileSharingEnabled": "it turns the container into a share point (ADR-0007, F19)",
    "LSSupportsOpeningDocumentsInPlace": "it turns the container into a share point (ADR-0007, F19)",
    "CFBundleURLTypes": "any app can claim a custom scheme; links arrive as verified universal links or not at all (§8.12)",
}
for key, why in forbidden.items():
    if key in info:
        print(f"Info.plist declares {key}: {why}")

# Face ID refuses an app with no usage description — on a physical device, invisible on the
# simulator, which does not even apply the item's biometric ACL (bitácora 0041). LumeMed learned it
# by a crash (its ADR-0015). Required as soon as iOS code asks for biometrics.
uses_biometrics = any(
    re.search(r"kSecAccessControlBiometry|LAContext", f.read_text(errors="replace"))
    for f in ios_main.rglob("*.kt")
)
if uses_biometrics and not str(info.get("NSFaceIDUsageDescription", "")).strip():
    print("Info.plist has no NSFaceIDUsageDescription, and iosMain uses biometrics: Face ID refuses the app on a device")

allowed_entitlements = {"keychain-access-groups"}
for key in sorted(set(ent) - allowed_entitlements):
    print(f"entitlement {key} is not on this gate's allowlist (an app group is a keychain group too); adding one needs an ADR")
groups = ent.get("keychain-access-groups", [])
if groups != ["$(AppIdentifierPrefix)$(CFBundleIdentifier)"]:
    print(f"keychain-access-groups is {groups!r}: it must be exactly the app's own group — a shared one is readable by every app the same team signs, LumeMed included (ADR-0001)")
PY
}
problems=$(plist_problems)
[ -n "$problems" ] && fail "ios-host: the Info.plist or the entitlements break the host's posture" \
    "Parsed, the way iOS reads them." "$problems"

# ── What the host LINKS (task 0002, F17) ────────────────────────────────────────────────────────
# §8.1's brake on crash and analytics SDKs is "doble y con gate", and both halves were Gradle-only:
# a Sentry `import` plus a Swift package reference in the project passed every gate (reproduced).
# The host links the Kotlin framework and Apple's UI frameworks, nothing else; a dependency here
# needs an ADR and a committed Package.resolved, exactly like a Gradle one.
pkg=$(grep -nE 'XCRemoteSwiftPackageReference|XCLocalSwiftPackageReference|XCSwiftPackageProductDependency' "$PROJECT" || true)
[ -n "$pkg" ] && fail "ios-host: the Xcode project depends on a Swift package" \
    "A dependency of the host goes through an ADR (§8.8, ADR-0018), not through Xcode." "$pkg"
managers=$(find iosApp \( -name Podfile -o -name Package.swift -o -name Cartfile -o -name Package.resolved \) -not -path '*/build/*' 2>/dev/null)
[ -n "$managers" ] && fail "ios-host: a dependency manager file in the host" \
    "The host has no third-party dependency (§8.8)." "$managers"
imports=$(for f in $(find iosApp -name '*.swift' -not -path '*/build/*' 2>/dev/null); do
    python3 Scripts/lib/uncomment.py --lang c --strip-strings "$f" 2>/dev/null |
        grep -nE '^[[:space:]]*(@testable[[:space:]]+)?import[[:space:]]+' | sed "s|^|$f:|"
done | grep -vE 'import[[:space:]]+(SwiftUI|UIKit|Foundation|LumeMedLink)[[:space:]]*$' || true)
[ -n "$imports" ] && fail "ios-host: the host imports a module it is not allowed to link" \
    "Allowed: SwiftUI, UIKit, Foundation and the Kotlin framework. Anything else is a dependency, and needs an ADR (§8.1, §8.8)." "$imports"

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
