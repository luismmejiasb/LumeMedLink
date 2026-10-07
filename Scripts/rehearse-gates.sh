#!/bin/sh
# Bait rehearsal (ADR-0029).
#
# "Cada gate se ensaya con archivo-cebo antes de confiar en su verde" has been this repo's rule
# since S0.2, and it was followed — by hand, once, by whoever wrote the gate. That is exactly the
# weakness: the author of a gate writes the bait their gate catches, because it is the same head.
# Two gates were later walked through by bait an outside auditor wrote, and nothing regressed to
# make them fail — they had been blind since birth.
#
# So the rehearsal becomes an artifact that RUNS. Each case below deletes or disguises a real
# control and asserts the gate turns RED. A gate that stays green with its control gone is not a
# gate, and this script is what says so out loud.
#
# It never touches the working tree: every bait is applied to a COPY.
#
# The copy is of the working tree, deliberately, and the first draft of this script got that wrong:
# it rehearsed a `git worktree` of HEAD while the gate fixes sat uncommitted, so it reported eleven
# undetected baits against gates that had already been repaired. A rehearsal that measures the
# committed state while you are editing is the same stale-artifact defect the gates themselves are
# here to prevent — so this one measures what you have NOW.
#
# Usage: Scripts/rehearse-gates.sh   (exit non-zero if any bait goes undetected)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

WORK=$(mktemp -d "${TMPDIR:-/tmp}/lume-rehearse.XXXXXX") || exit 1
TREE="$WORK/tree"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT INT TERM

# Source only: build outputs are large, irrelevant to every gate here, and one of them (the merged
# manifest) would make check-network-posture read a stale artifact instead of skipping that half.
sync_tree() {
    rm -rf "$TREE"
    mkdir -p "$TREE"
    tar -cf - --exclude='./.git' --exclude='*/build' --exclude='./build' --exclude='./.gradle' \
        --exclude='*.class' --exclude='./dd' . 2>/dev/null | (cd "$TREE" && tar -xf -) || return 1
}
sync_tree || { echo "rehearse: could not copy the tree"; exit 1; }

MANIFEST="$TREE/androidApp/src/main/AndroidManifest.xml"
ACTIVITY="$TREE/androidApp/src/main/kotlin/com/luismejias/lumemedlink/android/MainActivity.kt"
APPDELEGATE="$TREE/iosApp/iosApp/AppDelegate.swift"

PASS=0
FAIL=0

restore() { sync_tree; }

# bait <name> <gate> <python-edit>
#   Applies the edit, runs the gate FROM THE WORKTREE, and requires a non-zero exit.
bait() {
    name=$1
    gate=$2
    edit=$3
    restore
    if ! printf '%s' "$edit" | python3 - "$TREE" >/dev/null 2>&1; then
        echo "  ERROR  $name — the bait itself could not be applied"
        FAIL=$((FAIL + 1))
        return
    fi
    # The interpreter comes from the extension: the gates here are shell, the numbered-docs one is
    # Python. Running a .py under `sh` would fail for the wrong reason and read as a caught bait.
    case "$gate" in
        *.py) runner=python3 ;;
        *) runner=sh ;;
    esac
    if (cd "$TREE" && "$runner" "Scripts/$gate" >/dev/null 2>&1); then
        echo "  GREEN  $name  -> $gate DID NOT CATCH IT"
        FAIL=$((FAIL + 1))
    else
        echo "  red    $name  ($gate)"
        PASS=$((PASS + 1))
    fi
}

echo "rehearsing gates against bait..."

# ── The manifest: a parser must not be foolable by a comment or by the wrong element ────────────
COMMENT_BAIT='
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/AndroidManifest.xml"
s = p.read_text()
attrs = ["android:allowBackup=\"false\"",
         "android:dataExtractionRules=\"@xml/data_extraction_rules\"",
         "android:usesCleartextTraffic=\"false\"",
         "android:networkSecurityConfig=\"@xml/network_security_config\""]
for a in attrs:
    for pad in ("\n            ", "\n        ", "\n    "):
        s = s.replace(pad + a, "")
s = s.replace("<application", "<!-- Security posture, for reviewers:\n" + "\n".join("         " + a for a in attrs) + " -->\n    <application", 1)
p.write_text(s)
import xml.dom.minidom; xml.dom.minidom.parse(str(p))
'
bait "four posture attributes hidden in a multi-line XML comment" check-network-posture.sh "$COMMENT_BAIT"
bait "four posture attributes hidden in a multi-line XML comment" check-backup-posture.sh "$COMMENT_BAIT"

bait "allowBackup declared on the wrong element" check-backup-posture.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/AndroidManifest.xml"
s = p.read_text().replace("android:allowBackup=\"false\"", "", 1)
s = s.replace("<activity", "<activity android:allowBackup=\"false\"", 1)
p.write_text(s)
'
bait "allowBackup flipped to true" check-backup-posture.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/AndroidManifest.xml"
p.write_text(p.read_text().replace("android:allowBackup=\"false\"", "android:allowBackup=\"true\""))
'
bait "cleartext re-permitted" check-network-posture.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/AndroidManifest.xml"
p.write_text(p.read_text().replace("android:usesCleartextTraffic=\"false\"", "android:usesCleartextTraffic=\"true\""))
'
bait "a lock-screen surface declared with a trailing comment on the same line" check-preauth-surfaces.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/AndroidManifest.xml"
p.write_text(p.read_text().replace("<activity", "<activity android:showWhenLocked=\"true\" <!-- temporary, remove before release -->", 1))
'

# ── The shell: the token must be a CALL, in main/, outside comments and strings ─────────────────
STRIP='
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/kotlin/com/luismejias/lumemedlink/android/MainActivity.kt"
s = p.read_text()
s = re.sub(r"\n\s*window\.setFlags\([^)]*\)", "", s)
s = re.sub(r"\n\s*window\.decorView\.filterTouchesWhenObscured\s*=\s*true", "", s)
s = re.sub(r"\n\s*window\.decorView\.denyAutofillExport\(\)", "", s)
s = re.sub(r"\n\s*denyContentCapture\(\)", "", s)
p.write_text(s)
'
IN_TEST=$STRIP'
d = pathlib.Path(sys.argv[1]) / "androidApp/src/test/kotlin/com/luismejias/lumemedlink/android"
d.mkdir(parents=True, exist_ok=True)
(d / "WindowHardeningContractTest.kt").write_text(
    "package com.luismejias.lumemedlink.android\n\nclass WindowHardeningContractTest {\n"
    "    private val contract = listOf(\n"
    "        \"FLAG_SECURE\",\n        \"filterTouchesWhenObscured\",\n"
    "        \"window.decorView.denyAutofillExport()\",\n        \"denyContentCapture()\",\n    )\n}\n")
'
bait "controls deleted, tokens live in a test file" check-screen-security.sh "$IN_TEST"
bait "controls deleted, tokens live in a test file" check-input-surfaces.sh "$IN_TEST"

IN_BLOCK=$STRIP'
s = p.read_text().replace("class MainActivity",
  "/*\n window.setFlags(FLAG_SECURE, FLAG_SECURE)\n window.decorView.filterTouchesWhenObscured = true\n"
  " window.decorView.denyAutofillExport()\n denyContentCapture()\n*/\nclass MainActivity", 1)
p.write_text(s)
'
bait "controls deleted, tokens live inside a /* */ block" check-screen-security.sh "$IN_BLOCK"
bait "controls deleted, tokens live inside a /* */ block" check-input-surfaces.sh "$IN_BLOCK"

IN_STRING=$STRIP'
s = p.read_text().replace("class MainActivity",
  "private val documented = listOf(\n"
  "    \"window.setFlags(FLAG_SECURE, FLAG_SECURE)\",\n"
  "    \"window.decorView.filterTouchesWhenObscured = true\",\n"
  "    \"window.decorView.denyAutofillExport()\",\n    \"denyContentCapture()\",\n)\n\nclass MainActivity", 1)
p.write_text(s)
'
bait "controls deleted, tokens live in string literals in main/" check-screen-security.sh "$IN_STRING"
bait "controls deleted, tokens live in string literals in main/" check-input-surfaces.sh "$IN_STRING"

bait "tapjacking guard assigned false" check-screen-security.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/kotlin/com/luismejias/lumemedlink/android/MainActivity.kt"
p.write_text(p.read_text().replace("filterTouchesWhenObscured = true", "filterTouchesWhenObscured = false"))
'
bait "autofill exclusion moved off the decor view" check-input-surfaces.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/kotlin/com/luismejias/lumemedlink/android/MainActivity.kt"
p.write_text(p.read_text().replace("window.decorView.denyAutofillExport()", "window.decorView.rootView.denyAutofillExport()"))
'

# ── The gates the audit found blind, one bait each (ADR-0029, second pass) ──────────────────────
# A design-kit field is a text field the hardened primitive never sees. The raw-field pattern
# cannot match it: there is no word boundary between `Lume` and `TextField` (ADR-0013 amendment
# of 2026-10-06; LumeUIComposer is the kit).
bait "a design-kit text field in a screen, outside the hardened primitive" check-input-surfaces.sh '
import sys, pathlib
root = pathlib.Path(sys.argv[1])
f = root / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/features/bait/BaitScreen.kt"
f.parent.mkdir(parents=True, exist_ok=True)
f.write_text("package com.luismejias.lumemedlink.features.bait\n\nfun bait() { LumeTextField(value = \"\", onValueChange = {}) }\n")
'

bait "a clinical name as a function parameter, not a property" check-data-boundary.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session"
(d / "BaitBoundary.kt").write_text(
    "package com.luismejias.lumemedlink.core.session\n\n"
    "internal fun render(motivoClinico: String) = motivoClinico\n")
'
bait "a clinical name as a type, not a property" check-data-boundary.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session"
(d / "BaitBoundary.kt").write_text(
    "package com.luismejias.lumemedlink.core.session\n\ninternal class AllergyBanner\n")
'
bait "the distribution checksum emptied but the line kept" check-wrapper.sh '
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / "gradle/wrapper/gradle-wrapper.properties"
p.write_text(re.sub(r"^distributionSha256Sum=.*$", "distributionSha256Sum=", p.read_text(), flags=re.M))
'
bait "the distribution served from another host" check-wrapper.sh '
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / "gradle/wrapper/gradle-wrapper.properties"
p.write_text(re.sub(r"^distributionUrl=.*$", r"distributionUrl=https\\://evil.test/distributions/gradle-9.7.1-bin.zip", p.read_text(), flags=re.M))
'
bait "a CI action pinned to a tag the denylist never named" check-dependency-allowlist.sh '
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / ".github/workflows/ci.yml"
s = p.read_text()
before = s
s = re.sub(r"uses: actions/checkout@[0-9a-f]{40}", "uses: actions/checkout@latest", s, count=1)
if s == before: raise SystemExit("bait did not apply")
p.write_text(s)
'
bait "a package named core under androidApp, stashing a token" check-feature-isolation.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "androidApp/src/main/kotlin/com/luismejias/lumemedlink/core"
d.mkdir(parents=True, exist_ok=True)
(d / "Sneaky.kt").write_text(
    "package com.luismejias.lumemedlink.core\n\nimport android.content.Context\n\n"
    "fun stash(c: Context, t: String) {\n"
    "    c.getSharedPreferences(\"s\", Context.MODE_PRIVATE).edit().putString(\"t\", t).apply()\n}\n")
'
bait "a package named core under androidApp, stashing a token" check-forbidden-patterns.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "androidApp/src/main/kotlin/com/luismejias/lumemedlink/core"
d.mkdir(parents=True, exist_ok=True)
(d / "Sneaky.kt").write_text(
    "package com.luismejias.lumemedlink.core\n\nimport android.content.Context\n\n"
    "fun stash(c: Context, t: String) {\n"
    "    c.getSharedPreferences(\"s\", Context.MODE_PRIVATE).edit().putString(\"t\", t).apply()\n}\n")
'
bait "a session token to logcat from inside core/, via an import" check-logging.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "composeApp/src/androidMain/kotlin/com/luismejias/lumemedlink/core/session"
(d / "BaitLog.kt").write_text(
    "package com.luismejias.lumemedlink.core.session\n\nimport android.util.Log\n\n"
    "internal fun leak(token: String) {\n    Log.d(\"lume\", token)\n}\n")
'

bait "a notification surface in the iOS host, which had no half of this rule" check-preauth-surfaces.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/AppDelegate.swift"
p.write_text(p.read_text() + "\n\nfunc baitPreauth() {\n    let c = UNUserNotificationCenter.current()\n    _ = c\n}\n")
'
bait "UserDefaults in the iOS host, where P3 never reached" check-preauth-surfaces.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/AppDelegate.swift"
p.write_text(p.read_text() + "\n\nfunc baitDefaults() {\n    UserDefaults.standard.set(1, forKey: \"k\")\n}\n")
'

# ── The logout contract (ADR-0014) ──────────────────────────────────────────────────────────────
bait "logout erases one key instead of the namespace" check-logout-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session/LogoutContract.kt"
p.write_text(p.read_text().replace("secureStore.wipe()", "secureStore.remove(SecureStoreKey.SESSION_TOKENS.storageKey)"))
'
bait "the erase becomes cancellable again" check-logout-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session/LogoutContract.kt"
p.write_text(p.read_text().replace("withContext(NonCancellable)", "run"))
'
bait "a declared step is never attempted" check-logout-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session/LogoutContract.kt"
p.write_text(p.read_text().replace("step(LogoutStep.TIER2_MATERIAL) { unlockGate.clear() }", ""))
'
bait "the shell re-inlines the erase instead of calling the contract" check-logout-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/App.kt"
s = p.read_text().replace("val outcome = performLogout(sessionManager, secureStore, unlockGate, sessionLock)",
                          "sessionManager.logout()\n        unlockGate.clear()\n        sessionLock.sessionEnded()\n        val outcome = LogoutOutcome(emptySet())")
p.write_text(s)
'

bait "the prompt drops to DEVICE_CREDENTIAL while another line still spells the long word" check-biometric-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/androidMain/kotlin/com/luismejias/lumemedlink/core/session/BiometricUnlockGate.kt"
s = p.read_text()
before = s
s = s.replace("setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)",
              "setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)")
if s == before: raise SystemExit("bait did not apply")
p.write_text(s)
'

# ── The inactivity window (ADR-0032) ────────────────────────────────────────────────────────────
bait "the window is measured by the wall clock alone again" check-biometric-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session/InactivityLock.kt"
s = p.read_text()
before = s
s = s.replace("maxOf(byWallClock, byElapsedClock)", "byWallClock")
if s == before: raise SystemExit("bait did not apply")
p.write_text(s)
'
bait "the shell stops waiting for the window and only reacts to touches" check-biometric-contract.sh '
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/App.kt"
s = p.read_text()
before = s
s = s.replace("val remaining = sessionLock.millisUntilLock()", "val remaining = 60_000L")
if s == before: raise SystemExit("bait did not apply")
p.write_text(s)
'

# ── The iOS host ────────────────────────────────────────────────────────────────────────────────
bait "the relink guard is deleted from the build phase" check-ios-host.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp.xcodeproj/project.pbxproj"
s = p.read_text()
before = s
s = s.replace("rm -f \\\"$TARGET_BUILD_DIR/$EXECUTABLE_PATH\\\"", "true")
if s == before: raise SystemExit("bait did not apply")
p.write_text(s)
'
bait "the framework stamp is dropped, so nothing notices it moved" check-ios-host.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp.xcodeproj/project.pbxproj"
s = p.read_text().replace("kotlin-framework.stamp", "unused.tmp")
p.write_text(s)
'
bait "the cover goes back to the app-delegate method that scenes never call" check-ios-host.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/AppDelegate.swift"
s = p.read_text()
s = s.replace("UIScene.willDeactivateNotification", "UIApplication.willResignActiveNotification")
s += "\n\nextension AppDelegate {\n    func applicationWillResignActive(_ application: UIApplication) {}\n}\n"
p.write_text(s)
'
bait "the cover is armed but never taken down" check-ios-host.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/AppDelegate.swift"
p.write_text(p.read_text().replace("UIScene.didActivateNotification", "UIScene.didEnterBackgroundNotification"))
'
bait "keyboard veto survives only inside a /* */ block" check-ios-host.sh '
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/AppDelegate.swift"
s = p.read_text()
s = s.replace("return extensionPointIdentifier != .keyboard", "return true")
s = s.replace("import UIKit", "import UIKit\n/*\n shouldAllowExtensionPointIdentifier .keyboard is refused app-wide\n*/", 1)
p.write_text(s)
'

# ── The iOS session posture: the setter, with its value, inside the function production runs ────
# (task 0004). Each bait leaves the WORDS in the file and removes the CONTROL.
POSTURE_EDIT='
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/iosMain/kotlin/com/luismejias/lumemedlink/core/networking/PlatformHttpEngine.ios.kt"
s = p.read_text()
def swap(old, new):
    # UNIQUE, not merely present: the first draft of these baits replaced the first match, which for
    # one of them was the KDoc that quotes the line, so the bait edited a comment and the gate stayed
    # green for the right reason. A bait that can land in a comment is not a bait.
    global s
    assert s.count(old) == 1, "bait anchor must occur exactly once: " + repr(old)
    s = s.replace(old, new)
'
bait "the credential store left as the shared one" check-network-posture.sh "$POSTURE_EDIT"'
swap("    setURLCredentialStorage(null)\n", "")
p.write_text(s)
'
bait "cookies sent again, the false line flipped" check-network-posture.sh "$POSTURE_EDIT"'
swap("    setHTTPShouldSetCookies(false)\n", "    setHTTPShouldSetCookies(true)\n")
p.write_text(s)
'
bait "the cookie store nulled only inside a comment" check-network-posture.sh "$POSTURE_EDIT"'
swap("    setHTTPCookieStorage(null)\n", "    // setHTTPCookieStorage(null)\n")
p.write_text(s)
'
bait "the cache line moved to a helper nobody calls" check-network-posture.sh "$POSTURE_EDIT"'
swap("    setURLCache(null)\n", "")
s += "\ninternal fun NSURLSessionConfiguration.unusedPosture() {\n    setURLCache(null)\n}\n"
p.write_text(s)
'
bait "a later call in the posture switches the cache back on" check-network-posture.sh "$POSTURE_EDIT"'
swap("    setHTTPShouldSetCookies(false)\n", "    setHTTPShouldSetCookies(false)\n    setURLCache(NSURLCache.sharedURLCache)\n")
p.write_text(s)
'
bait "the engine stops registering the posture" check-network-posture.sh "$POSTURE_EDIT"'
swap("configureSession { applyLumeSessionPosture() }", "configureSession { }")
p.write_text(s)
'
bait "production builds an engine of its own beside the measured one" check-network-posture.sh "$POSTURE_EDIT"'
swap("HttpClientEngine = lumeDarwinEngine()", "HttpClientEngine = Darwin.create { }")
p.write_text(s)
'

# ── Deep links (F17, task 0011): https only, App Links verified — and the iOS half finally baited ──
LINK_EDIT='
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/AndroidManifest.xml"
s = p.read_text()
anchor = "            </intent-filter>\n        </activity>"
assert s.count(anchor) == 1, "bait anchor not found"
def add_filter(xml):
    global s
    s = s.replace(anchor, "            </intent-filter>\n" + xml + "        </activity>")
'
bait "a custom scheme on the main activity, for development" check-deep-links.sh "$LINK_EDIT"'
add_filter("""            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="lumemedlink" android:host="open" />
            </intent-filter>
""")
p.write_text(s)
'
bait "an https App Link that is not verified" check-deep-links.sh "$LINK_EDIT"'
add_filter("""            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="example.invalid" />
            </intent-filter>
""")
p.write_text(s)
'
bait "https and a custom scheme merged into one filter" check-deep-links.sh "$LINK_EDIT"'
add_filter("""            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="example.invalid" />
                <data android:scheme="lumemedlink" />
            </intent-filter>
""")
p.write_text(s)
'
bait "a redirect activity with a custom scheme, the way an auth library brings one" check-deep-links.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/AndroidManifest.xml"
s = p.read_text()
anchor = "    </application>"
assert s.count(anchor) == 1
s = s.replace(anchor, """        <activity android:name=".AuthRedirectReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="com.luismejias.lumemedlink.auth" />
            </intent-filter>
        </activity>
""" + anchor)
p.write_text(s)
'
bait "a custom URL scheme in the iOS host" check-ios-host.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/Info.plist"
s = p.read_text()
anchor = "<dict>"
assert anchor in s
s = s.replace(anchor, "<dict>\n\t<key>CFBundleURLTypes</key>\n\t<array>\n\t\t<dict>\n\t\t\t<key>CFBundleURLSchemes</key>\n\t\t\t<array>\n\t\t\t\t<string>lumemedlink</string>\n\t\t\t</array>\n\t\t</dict>\n\t</array>", 1)
p.write_text(s)
'

# ── Task 0002: the completeness pass's findings, each control removed while its words stay ──────
# F01 · coming back from sleep must re-ask the lock
bait "the shell stops re-asking the lock when the app comes back" check-biometric-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/App.kt"
s = p.read_text()
old = "                locked = sessionLock.isLocked()\n                returns += 1\n"
assert s.count(old) == 1
p.write_text(s.replace(old, "                returns += 1\n"))
'
# F03 · the key parameters, hidden where a line filter used to read them as code
bait "user authentication required, hidden inside a block comment" check-biometric-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/androidMain/kotlin/com/luismejias/lumemedlink/core/session/BiometricUnlockGate.kt"
s = p.read_text()
old = "        .setUserAuthenticationRequired(true)\n"
assert s.count(old) == 1
p.write_text(s.replace(old, "        /*\n        .setUserAuthenticationRequired(true) once QA has a fingerprint\n         */\n"))
'
bait "the iOS ACL flag replaced, its name left in a trailing comment" check-biometric-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/iosMain/kotlin/com/luismejias/lumemedlink/core/session/KeychainUnlockGate.kt"
s = p.read_text()
old = "            kSecAccessControlBiometryCurrentSet,\n"
assert s.count(old) == 1
p.write_text(s.replace(old, "            0u, // was kSecAccessControlBiometryCurrentSet\n"))
'
bait "the enrollment invalidation moved into a helper nobody calls" check-biometric-contract.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/androidMain/kotlin/com/luismejias/lumemedlink/core/session/BiometricUnlockGate.kt"
s = p.read_text()
old = "        .setInvalidatedByBiometricEnrollment(true)\n"
assert s.count(old) == 1
s = s.replace(old, "")
s += "\nprivate fun KeyGenParameterSpec.Builder.unused() = setInvalidatedByBiometricEnrollment(true)\n"
p.write_text(s)
'
# F02, F04 · the plist and the entitlements, as iOS reads them
IOS_PLIST_EDIT='
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/Info.plist"
s = p.read_text()
anchor = "\t<key>CFBundleDevelopmentRegion</key>"
assert s.count(anchor) == 1
'
bait "ATS switched off, with a comment that opens on the key line" check-ios-host.sh "$IOS_PLIST_EDIT"'
p.write_text(s.replace(anchor, "\t<key>NSAppTransportSecurity</key> <!-- TEMP: staging is plain http;\n\t     remove before TestFlight -->\n\t<dict><key>NSAllowsArbitraryLoads</key><true/></dict>\n" + anchor))
'
bait "the Face ID usage description removed" check-ios-host.sh '
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/Info.plist"
s = p.read_text()
s2 = re.sub(r"\t<key>NSFaceIDUsageDescription</key>\n\t<string>[^<]*</string>\n", "", s)
assert s2 != s
p.write_text(s2)
'
IOS_ENT_EDIT='
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/iosApp.entitlements"
s = p.read_text()
anchor = "\t\t<string>$(AppIdentifierPrefix)$(CFBundleIdentifier)</string>\n"
assert s.count(anchor) == 1
'
bait "a shared keychain group listed first, behind a trailing comment" check-ios-host.sh "$IOS_ENT_EDIT"'
p.write_text(s.replace(anchor, "\t\t<string>$(AppIdentifierPrefix)com.luismejias.lume.shared</string> <!-- SSO with LumeMed;\n -->\n" + anchor))
'
bait "an app group, which is a keychain group too" check-ios-host.sh "$IOS_ENT_EDIT"'
p.write_text(s.replace("\t<key>keychain-access-groups</key>", "\t<key>com.apple.security.application-groups</key>\n\t<array><string>group.com.luismejias.lume</string></array>\n\t<key>keychain-access-groups</key>"))
'
# F17 · what the iOS host links
bait "a crash SDK imported and started in the iOS host" check-ios-host.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/AppDelegate.swift"
s = p.read_text()
assert s.startswith("import UIKit\n")
p.write_text(s.replace("import UIKit\n", "import UIKit\nimport Sentry\n", 1))
'
bait "a Swift package referenced by the Xcode project" check-ios-host.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp.xcodeproj/project.pbxproj"
s = p.read_text()
anchor = "/* End PBXProject section */"
assert s.count(anchor) == 1
p.write_text(s.replace(anchor, anchor + "\n/* Begin XCRemoteSwiftPackageReference section */\n\t\t1A00000000000000000000FF /* XCRemoteSwiftPackageReference \"sentry-cocoa\" */ = {isa = XCRemoteSwiftPackageReference; repositoryURL = \"https://github.com/getsentry/sentry-cocoa\"; };\n/* End XCRemoteSwiftPackageReference section */"))
'
# F14 · FLAG_SECURE, cleared by spellings the old pattern accepted
SECURE_EDIT='
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / "androidApp/src/main/kotlin/com/luismejias/lumemedlink/android/MainActivity.kt"
s = p.read_text()
'
bait "FLAG_SECURE cleared by setFlags(0, FLAG_SECURE)" check-screen-security.sh "$SECURE_EDIT"'
s2 = re.sub(r"setFlags\(\s*WindowManager\.LayoutParams\.FLAG_SECURE,", "setFlags(0,", s, count=1)
assert s2 != s
p.write_text(s2)
'
bait "a popup opting out of FLAG_SECURE" check-screen-security.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app"
(d / "InsecurePopup.kt").write_text("package com.luismejias.lumemedlink.app\n\nimport androidx.compose.ui.window.PopupProperties\nimport androidx.compose.ui.window.SecureFlagPolicy\n\ninternal val previewPopup = PopupProperties(securePolicy = SecureFlagPolicy.SecureOff)\n")
'
# F15 · iOS lock-screen and launcher surfaces, called from Kotlin or Swift
bait "remote notifications and Spotlight called from iosMain Kotlin" check-preauth-surfaces.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "composeApp/src/iosMain/kotlin/com/luismejias/lumemedlink/app"
(d / "Donation.kt").write_text("package com.luismejias.lumemedlink.app\n\nimport platform.CoreSpotlight.CSSearchableIndex\nimport platform.UIKit.UIApplication\n\ninternal fun donate() {\n    UIApplication.sharedApplication.registerForRemoteNotifications()\n    CSSearchableIndex.defaultSearchableIndex()\n}\n")
'
bait "a home-screen quick action in the iOS host" check-preauth-surfaces.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/AppDelegate.swift"
p.write_text(p.read_text() + "\nfunc quickActions() { UIApplication.shared.shortcutItems = [UIApplicationShortcutItem(type: \"a\", localizedTitle: \"t\")] }\n")
'
bait "a notification built on a line that opens with a comment" check-preauth-surfaces.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "composeApp/src/androidMain/kotlin/com/luismejias/lumemedlink/app"
d.mkdir(parents=True, exist_ok=True)
(d / "Reminder.kt").write_text("package com.luismejias.lumemedlink.app\n\nimport android.content.Context\nimport androidx.core.app.NotificationCompat\n\ninternal fun remind(c: Context, text: String) =\n    /* reminder */ NotificationCompat.Builder(c, \"x\").setContentText(text)\n")
'
# F19 · the sentinel the launch actually uses
bait "the shell hands the launch an always-has-run sentinel" check-install-sentinel.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/App.kt"
s = p.read_text()
old = "    val installSentinel = remember { platformInstallSentinel() }\n"
assert s.count(old) == 1
p.write_text(s.replace(old, "    val installSentinel = remember {\n        object : com.luismejias.lumemedlink.core.session.InstallSentinel {\n            override suspend fun hasRunBefore(): Boolean = true\n            override suspend fun markHasRun() = Unit\n        }\n    }\n"))
'
bait "the marker excluded from backup with value = false" check-install-sentinel.sh '
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / "composeApp/src/iosMain/kotlin/com/luismejias/lumemedlink/core/session/PlatformInstallSentinel.ios.kt"
s = p.read_text()
s2 = re.sub(r"value = true,(\s*forKey = NSURLIsExcludedFromBackupKey)", r"value = false,\1", s)
assert s2 != s
p.write_text(s2)
'
# F20 · the release build made debuggable from outside its own block
bait "every build type made debuggable, release included" check-release-hardening.sh '
import sys, pathlib
p = pathlib.Path(sys.argv[1]) / "androidApp/build.gradle.kts"
s = p.read_text()
anchor = "    buildFeatures { compose = true }"
assert s.count(anchor) == 1
p.write_text(s.replace(anchor, "    buildTypes.configureEach { isDebuggable = true }\n\n" + anchor))
'
# F11 · broad catches where the cancellation guard did not look
bait "a broad catch with no ensureActive, in a per-target iOS source set" check-cancellation-guard.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "composeApp/src/iosArm64Main/kotlin/com/luismejias/lumemedlink/core/session"
(d / "Swallow.kt").write_text("package com.luismejias.lumemedlink.core.session\n\ninternal suspend fun swallow(block: suspend () -> Unit) {\n    try {\n        block()\n    } catch (e: Throwable) {\n        Unit\n    }\n}\n")
'
bait "an IllegalStateException catch — CancellationException is one" check-cancellation-guard.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session"
(d / "Swallow.kt").write_text("package com.luismejias.lumemedlink.core.session\n\ninternal suspend fun swallow(block: suspend () -> Unit) {\n    try {\n        block()\n    } catch (e: IllegalStateException) {\n        Unit\n    }\n}\n")
'
# url-hygiene had no bait at all
bait "a personal datum put in a query parameter" check-url-hygiene.sh '
import sys, pathlib
d = pathlib.Path(sys.argv[1]) / "composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/networking"
(d / "Lookup.kt").write_text("package com.luismejias.lumemedlink.core.networking\n\nimport io.ktor.client.HttpClient\nimport io.ktor.client.request.get\nimport io.ktor.client.request.parameter\n\ninternal suspend fun lookup(client: HttpClient, value: String) = client.get(\"v1/patients\") { parameter(\"email\", value) }\n")
'
# F08 + F17(deep links) · the MERGED manifest halves, which no bait could reach while build/ was excluded
MERGED_EDIT='
import sys, pathlib, shutil
root = pathlib.Path(sys.argv[1])
src = root / "androidApp/src/main/AndroidManifest.xml"
d = root / "androidApp/build/intermediates/merged_manifest/release/processReleaseMainManifest"
d.mkdir(parents=True, exist_ok=True)
m = d / "AndroidManifest.xml"
s = src.read_text()
'
bait "an advertising-id permission merged in by a dependency" check-network-posture.sh "$MERGED_EDIT"'
anchor = "    <uses-permission android:name=\"android.permission.INTERNET\" />"
assert s.count(anchor) == 1
m.write_text(s.replace(anchor, anchor + "\n    <uses-permission android:name=\"com.google.android.gms.permission.AD_ID\" />"))
'
bait "a library redirect activity with a custom scheme, only in the merge" check-deep-links.sh "$MERGED_EDIT"'
anchor = "    </application>"
assert s.count(anchor) == 1
m.write_text(s.replace(anchor, "        <activity android:name=\"net.example.RedirectReceiver\" android:exported=\"true\">\n            <intent-filter>\n                <action android:name=\"android.intent.action.VIEW\" />\n                <category android:name=\"android.intent.category.BROWSABLE\" />\n                <data android:scheme=\"net.example.auth\" />\n            </intent-filter>\n        </activity>\n" + anchor))
'

# ── The gate on the gates' rehearsal: every gate CI runs has at least one bait here ─────────────
# §9 says this script "borra cada control", and on 2026-10-07 four of the gates CI ran had no bait
# at all — which is exactly how two of them stayed walkable (task 0002, F20).
for g in $(grep -oE 'Scripts/check-[a-z-]+\.sh' .github/workflows/ci.yml | sort -u); do
    name=$(basename "$g")
    if ! grep -qE "^bait .* $name( |$)" "$0"; then
        echo "  GREEN  meta: $name runs in CI and has no bait in this rehearsal"
        FAIL=$((FAIL + 1))
    fi
done

# ── Numbered documents: the number IS the address, and nothing in this repo reads those trees ───
bait "two ADRs claim one number" numbered-docs-have-no-collisions.py '
import sys, pathlib, shutil
d = pathlib.Path(sys.argv[1]) / "docs/adr"
src = sorted(d.glob("0*.md"))[0]
shutil.copy(src, d / (src.name[:4] + "-bait-duplicate.md"))
'

bait "a numbered doc opens a namespace nobody walks" numbered-docs-have-no-collisions.py '
import sys, pathlib, shutil
root = pathlib.Path(sys.argv[1])
d = root / "docs/backend-answers"
d.mkdir(parents=True, exist_ok=True)
shutil.copy(sorted((root / "docs/adr").glob("0*.md"))[0], d / "0001-bait.md")
'

bait "a number that cannot be addressed" numbered-docs-have-no-collisions.py '
import sys, pathlib
(pathlib.Path(sys.argv[1]) / "docs/bitacora/0099b-bait.md").write_text("# bait\n")
'

restore

echo ""
if [ $FAIL -eq 0 ]; then
    echo "rehearse-gates: OK — $PASS baits, every one caught"
else
    echo "rehearse-gates: $FAIL of $((PASS + FAIL)) baits WENT UNDETECTED"
    exit 1
fi
