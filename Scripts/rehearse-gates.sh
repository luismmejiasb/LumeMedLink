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
    if (cd "$TREE" && sh "Scripts/$gate" >/dev/null 2>&1); then
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

# ── The iOS host ────────────────────────────────────────────────────────────────────────────────
bait "keyboard veto survives only inside a /* */ block" check-ios-host.sh '
import sys, pathlib, re
p = pathlib.Path(sys.argv[1]) / "iosApp/iosApp/AppDelegate.swift"
s = p.read_text()
s = s.replace("return extensionPointIdentifier != .keyboard", "return true")
s = s.replace("import UIKit", "import UIKit\n/*\n shouldAllowExtensionPointIdentifier .keyboard is refused app-wide\n*/", 1)
p.write_text(s)
'

restore

echo ""
if [ $FAIL -eq 0 ]; then
    echo "rehearse-gates: OK — $PASS baits, every one caught"
else
    echo "rehearse-gates: $FAIL of $((PASS + FAIL)) baits WENT UNDETECTED"
    exit 1
fi
