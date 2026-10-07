#!/bin/sh
# F7 / ADR-0028 — proves on a real iOS runtime that a reinstall does not inherit the previous
# installation's secrets, and proves the PREMISE that makes the guard necessary.
#
# WHY A SCRIPT AND NOT A TEST: the fact under test is what survives DELETING THE APP. No test
# process can delete its own app. The only honest instrument is the simulator plus `simctl`.
#
# THE CONTROL IS THE POINT. Step 2 must find the secret STILL THERE after the app was deleted — if
# it does not, the premise is false, the guard defends nothing, and every later assertion is
# meaningless. A run where step 2 says ABSENT is a FAILED run, not a clean one.
#
# It works by temporarily adding an OBSERVER to the iOS host: on every launch it reads a synthetic
# secret from this app's Keychain service, writes what it found into the container, and then seeds
# the secret again. The observation file is read from outside with `simctl get_app_container`. The
# observer is removed before the script exits, including on failure.
#
#   launch A1 (install 1) : the container is fresh, so the sentinel purges — including the seed the
#                           observer just wrote. A1 therefore ends with NOTHING, and the first draft
#                           of this script stopped here and concluded the premise was false. It was
#                           reading the guard's success as its own instrument's failure.
#   launch A2 (relaunch)  : container already marked, so no purge. The seed survives. This launch
#                           exists only to establish something for the delete to fail to destroy.
#   uninstall             : container destroyed, Keychain is NOT
#   launch B (install 2)  : PRESENT  ← THE PREMISE. The Keychain outlived the delete.
#                            then the sentinel runs and purges
#   launch C (relaunch)   : ABSENT   ← THE FIX. The inherited secret is gone.
#
# With --with-live-control the whole thing runs a second time with the boundary disabled, and
# launch C must come back PRESENT — the guard is proven load-bearing rather than assumed.
#
# Usage: Scripts/verify-install-sentinel.sh [--with-live-control] [--device <udid>]

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

BUNDLE_ID="com.luismejias.lumemedlink"
HOST_DIR="iosApp/iosApp"
OBSERVER="$HOST_DIR/InstallSentinelObserver.swift"
PROBE="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/SessionProbe.kt"
WORK=$(mktemp -d "${TMPDIR:-/tmp}/lume-sentinel.XXXXXX") || exit 1
PROBE_BACKUP="$WORK/SessionProbe.kt.orig"
# OWN DerivedData, never Xcode's shared one. The shared folder is keyed by the project's PATH, so a
# checkout that moved (or a second clone) leaves another `iosApp-<hash>` beside this one, and the
# `find … | head -1` this script used to run picked whichever came first — it could install a
# build of different, older code and report on it. A verifier that can measure the wrong binary is
# the stale-artifact defect of ADR-0028 and ADR-0030 one level up.
DD="$WORK/dd"
APP="$DD/Build/Products/Debug-iphonesimulator/LumeMedLink.app"
DEVICE=""
LIVE_CONTROL=0

while [ $# -gt 0 ]; do
    case "$1" in
        --with-live-control) LIVE_CONTROL=1 ;;
        --device) shift; DEVICE="${1:-}" ;;
        *) echo "unknown argument: $1"; exit 2 ;;
    esac
    shift
done

if [ -z "$DEVICE" ]; then
    # Only when exactly ONE simulator is booted. With several sessions on one Mac, "the first
    # booted one" can be another repo's simulator, and this script installs, uninstalls and seeds
    # Keychain items on whatever it picks. Guessing is not allowed: pass `--device "$(lume-sim …)"`.
    BOOTED=$(xcrun simctl list devices available 2>/dev/null | grep "(Booted)" | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')
    if [ "$(printf '%s\n' "$BOOTED" | grep -c .)" -gt 1 ]; then
        echo "FAIL more than one simulator is booted; pass --device <udid> instead of letting this guess."
        exit 1
    fi
    DEVICE=$BOOTED
fi
if [ -z "$DEVICE" ]; then
    echo "FAIL no booted simulator. Boot one, or pass --device <udid>."
    exit 1
fi

# Undoes EVERY edit this script makes to the working tree, on any exit. It used to undo two of the
# four: an early exit (a host that failed to build, an interrupt) deleted the observer file and
# left `AppDelegate.swift` calling it and the project listing it — a tree that no longer compiled,
# with nothing saying why. Each step is idempotent, so a normal exit that already cleaned up loses
# nothing by running it again.
cleanup() {
    rm -f "$OBSERVER"
    python3 - "$HOST_DIR/AppDelegate.swift" "iosApp/iosApp.xcodeproj/project.pbxproj" <<'PYEOF'
import sys
delegate, project = sys.argv[1], sys.argv[2]
s = open(delegate).read()
open(delegate, "w").write(s.replace("        InstallSentinelObserver.observeThenSeed()\n", ""))
s = open(project).read()
open(project, "w").write("".join(l for l in s.splitlines(True) if "InstallSentinelObserver" not in l))
PYEOF
    [ -s "$PROBE_BACKUP" ] && cp "$PROBE_BACKUP" "$PROBE"
    rm -rf "$WORK"
}
trap cleanup EXIT
# An interrupt EXITS (and so cleans up once) instead of cleaning up and carrying on building.
trap 'exit 130' INT TERM

cp "$PROBE" "$PROBE_BACKUP"

write_observer() {
    cat > "$OBSERVER" <<'SWIFTEOF'
import Foundation
import Security

// TEMPORARY — written and removed by Scripts/verify-install-sentinel.sh. Never committed.
// Reads a synthetic secret from this app's Keychain service, records what it found in the
// container, and seeds it again. All data is synthetic (§9).
enum InstallSentinelObserver {
    private static let service = "com.luismejias.lumemedlink.session"
    private static let account = "sentinel_probe_synthetic"
    private static let secret = "synthetic-inherited-secret"

    static func observeThenSeed() {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecUseDataProtectionKeychain as String: true,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var out: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &out)
        let verdict: String
        switch status {
        case errSecSuccess: verdict = "PRESENT"
        case errSecItemNotFound: verdict = "ABSENT"
        default: verdict = "ERROR(\(status))"
        }

        if let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
            try? FileManager.default.createDirectory(at: support, withIntermediateDirectories: true)
            try? verdict.write(to: support.appendingPathComponent("observation.txt"), atomically: true, encoding: .utf8)
        }

        query.removeValue(forKey: kSecReturnData as String)
        query.removeValue(forKey: kSecMatchLimit as String)
        SecItemDelete(query as CFDictionary)
        query[kSecValueData as String] = secret.data(using: .utf8)!
        query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
    }
}
SWIFTEOF
    # Call it as the very first thing the host does, BEFORE Compose composes and therefore before
    # the boundary runs. That ordering is what makes launch B able to see the inherited secret.
    #
    # Anchored to the SIGNATURE of didFinishLaunching, not to its body. The body-shaped anchor this
    # used before (`-> Bool { return true }`) stopped existing on 2026-09-21, when ADR-0031 gave the
    # method a real body, and the failed assert was ignored: the observer file was written, listed
    # in the project and never CALLED, so every launch read NO-FILE and the run blamed the premise.
    # The instrument broke and the verdict pointed at the thing being measured.
    python3 - "$HOST_DIR/AppDelegate.swift" <<'PYEOF'
import re, sys
p = sys.argv[1]
s = open(p).read()
m = re.search(r"didFinishLaunchingWithOptions[^)]*\)\s*->\s*Bool\s*\{\n", s)
if m is None:
    raise SystemExit("didFinishLaunchingWithOptions not found in AppDelegate.swift")
s = s[:m.end()] + "        InstallSentinelObserver.observeThenSeed()\n" + s[m.end():]
open(p, "w").write(s)
PYEOF
}

remove_observer() {
    rm -f "$OBSERVER"
    python3 - "$HOST_DIR/AppDelegate.swift" <<'PYEOF'
import sys
p = sys.argv[1]
s = open(p).read().replace("        InstallSentinelObserver.observeThenSeed()\n", "")
open(p, "w").write(s)
PYEOF
}

add_observer_to_project() {
    python3 - <<'PYEOF'
p = "iosApp/iosApp.xcodeproj/project.pbxproj"
s = open(p).read()
if "InstallSentinelObserver.swift" in s:
    raise SystemExit(0)
s = s.replace(
    "\t\t1A0000000000000000000008 /* AppDelegate.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = AppDelegate.swift; sourceTree = \"<group>\"; };",
    "\t\t1A0000000000000000000008 /* AppDelegate.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = AppDelegate.swift; sourceTree = \"<group>\"; };\n\t\t1A0000000000000000000017 /* InstallSentinelObserver.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = InstallSentinelObserver.swift; sourceTree = \"<group>\"; };")
s = s.replace(
    "\t\t\t\t1A0000000000000000000008 /* AppDelegate.swift */,",
    "\t\t\t\t1A0000000000000000000008 /* AppDelegate.swift */,\n\t\t\t\t1A0000000000000000000017 /* InstallSentinelObserver.swift */,")
s = s.replace(
    "\t\t\t\t1A000000000000000000000B /* AppDelegate.swift in Sources */,",
    "\t\t\t\t1A000000000000000000000B /* AppDelegate.swift in Sources */,\n\t\t\t\t1A0000000000000000000018 /* InstallSentinelObserver.swift in Sources */,")
s = s.replace(
    "\t\t1A000000000000000000000B /* AppDelegate.swift in Sources */ = {isa = PBXBuildFile; fileRef = 1A0000000000000000000008 /* AppDelegate.swift */; };",
    "\t\t1A000000000000000000000B /* AppDelegate.swift in Sources */ = {isa = PBXBuildFile; fileRef = 1A0000000000000000000008 /* AppDelegate.swift */; };\n\t\t1A0000000000000000000018 /* InstallSentinelObserver.swift in Sources */ = {isa = PBXBuildFile; fileRef = 1A0000000000000000000017 /* InstallSentinelObserver.swift */; };")
open(p, "w").write(s)
PYEOF
}

remove_observer_from_project() {
    python3 - <<'PYEOF'
p = "iosApp/iosApp.xcodeproj/project.pbxproj"
s = open(p).read()
for line in [l for l in s.split("\n") if "InstallSentinelObserver" in l]:
    s = s.replace(line + "\n", "")
open(p, "w").write(s)
PYEOF
}

# SIGNED, unlike the other verifiers: what this one measures is the Keychain, and an unsigned
# build has no keychain-access-groups entitlement — every SecItem call fails and the run reads
# ERROR(...) instead of PRESENT/ABSENT (the host's first launch crashed on exactly that, ADR-0025).
build_and_install() {
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
        -sdk iphonesimulator -destination "generic/platform=iOS Simulator" \
        -derivedDataPath "$DD" build > "$WORK/build.log" 2>&1 || {
        echo "FAIL the host did not build"; tail -20 "$WORK/build.log"; exit 1; }
    [ -d "$APP" ] || { echo "FAIL no built .app at $APP"; exit 1; }
    xcrun simctl install "$DEVICE" "$APP" >/dev/null 2>&1
}

observe() {
    xcrun simctl launch "$DEVICE" "$BUNDLE_ID" >/dev/null 2>&1
    sleep 4
    C=$(xcrun simctl get_app_container "$DEVICE" "$BUNDLE_ID" data 2>/dev/null)
    cat "$C/Library/Application Support/observation.txt" 2>/dev/null || echo "NO-FILE"
    xcrun simctl terminate "$DEVICE" "$BUNDLE_ID" >/dev/null 2>&1
}

run_cycle() {
    label="$1"
    echo ""
    echo "── $label ──"
    xcrun simctl uninstall "$DEVICE" "$BUNDLE_ID" >/dev/null 2>&1
    build_and_install
    a=$(observe); echo "  launch A1 (instalación fresca) : $a"
    a2=$(observe); echo "  launch A2 (siembra que perdura): $a2"
    xcrun simctl uninstall "$DEVICE" "$BUNDLE_ID" >/dev/null 2>&1
    xcrun simctl install "$DEVICE" "$APP" >/dev/null 2>&1
    b=$(observe); echo "  launch B (tras reinstalar)     : $b   ← LA PREMISA"
    c=$(observe); echo "  launch C (tras el sentinel)    : $c   ← EL ARREGLO"
    CYCLE_B="$b"; CYCLE_C="$c"
}

echo "device: $DEVICE"
# A broken INSTRUMENT is not a verdict about the premise. If the observer cannot be installed, say
# so and stop, instead of running launches that can only ever read NO-FILE.
write_observer || { echo "FAIL the observer could not be injected into AppDelegate.swift (instrument, not premise)"; exit 1; }
add_observer_to_project
[ "$(grep -c InstallSentinelObserver iosApp/iosApp.xcodeproj/project.pbxproj)" -eq 4 ] ||
    { echo "FAIL the observer could not be added to the Xcode project (instrument, not premise)"; exit 1; }

FAIL=0
run_cycle "CON el sentinel (comportamiento de producción)"
if [ "$CYCLE_B" != "PRESENT" ]; then
    echo ""
    echo "FAIL la PREMISA no se sostiene: el Keychain no sobrevivió a borrar la app (launch B = $CYCLE_B)."
    echo "  Sin esa premisa el guardián no defiende nada y el resto de esta corrida no significa nada."
    FAIL=1
fi
if [ "$CYCLE_C" != "ABSENT" ]; then
    echo ""
    echo "FAIL el secreto heredado SIGUE AHÍ tras el sentinel (launch C = $CYCLE_C)."
    echo "  Una reinstalación estaría heredando la sesión de quien tuvo el teléfono antes (§8.13, §8.17)."
    FAIL=1
fi

if [ $LIVE_CONTROL -eq 1 ]; then
    python3 - "$PROBE" <<'PYEOF'
import sys
p = sys.argv[1]
s = open(p).read()
s = s.replace("when (enforceInstallBoundary(sentinel, secureStore)) {",
              "when (InstallBoundary.ALREADY_ESTABLISHED) { // LIVE CONTROL: boundary disabled")
open(p, "w").write(s)
PYEOF
    run_cycle "CONTROL EN VIVO — sin el sentinel"
    if [ "$CYCLE_C" != "PRESENT" ]; then
        echo ""
        echo "FAIL el control en vivo NO reprodujo el agujero (launch C = $CYCLE_C)."
        echo "  Si el secreto desaparece sin el guardián, algo más lo está borrando y la corrida"
        echo "  de arriba pasó por la razón equivocada."
        FAIL=1
    fi
    cp "$PROBE_BACKUP" "$PROBE"
fi

remove_observer
remove_observer_from_project

echo ""
if [ $FAIL -eq 0 ]; then
    echo "install-sentinel: OK — la premisa se sostiene y el secreto heredado se destruye."
else
    exit 1
fi
