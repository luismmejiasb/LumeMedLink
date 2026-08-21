#!/bin/sh
# Repeatable proof of the single most important property of the tier-2 gate (F4, ADR-0005/0011):
# **enrolling a new biometric destroys the unlock key**, so whoever holds the phone cannot add
# their own fingerprint and inherit the doctor's session.
#
# This is a DEVICE proof and cannot run in CI (it needs a booted emulator and drives the system
# settings UI). It exists so the claim is reproducible on demand instead of being a one-time
# anecdote in a bitácora — run it before trusting the claim again.
#
# WHY IT USES `am instrument` AND NOT GRADLE, which cost this session a false positive:
# `connectedAndroidDeviceTest` reinstalls the test APK, and Android wipes an app's Keystore entries
# on uninstall (the very asymmetry ADR-0005 declares). So the key vanished between two Gradle runs
# for a reason that had nothing to do with biometrics, and the phase-B assertion "failed" in a way
# that looked like a security finding. Running the already-installed instrumentation directly keeps
# the package — and therefore the key — alive between phases.
#
# THE CONTROL IS NOT OPTIONAL. Step 2 runs phase B BEFORE any new enrollment and REQUIRES it to
# fail. Without that, "the key is gone" proves nothing: it could be a reinstall, a wiped emulator,
# or a key that was never created. The control is what makes step 4 evidence.
#
# Usage: Scripts/verify-tier2-invalidation.sh   (needs a booted emulator with a PIN + 1 fingerprint)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
RUNNER="com.luismejias.lumemedlink.test/androidx.test.runner.AndroidJUnitRunner"
CLASS="com.luismejias.lumemedlink.core.session.UnlockKeyInvalidationTest"
PIN="${LUME_TEST_PIN:-1234}"

[ -x "$ADB" ] || { echo "FAIL adb not found at $ADB"; exit 1; }
[ -n "$("$ADB" devices | sed -n '2p')" ] || { echo "FAIL no device/emulator attached"; exit 1; }

phase() {
    "$ADB" shell am instrument -w -e class "$CLASS#$1" "$RUNNER" 2>&1
}

# Taps the wizard's bottom-right button, computed from the real screen size rather than hardcoded.
tap_primary() {
    size=$("$ADB" shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p')
    w=$(echo "$size" | cut -d' ' -f1); h=$(echo "$size" | cut -d' ' -f2)
    "$ADB" shell input tap $((w * 84 / 100)) $((h * 935 / 1000)) >/dev/null 2>&1
}

enroll_one_more_fingerprint() {
    "$ADB" shell am start -a android.settings.FINGERPRINT_ENROLL >/dev/null 2>&1; sleep 4
    "$ADB" shell input text "$PIN" >/dev/null 2>&1; sleep 1
    "$ADB" shell input keyevent KEYCODE_ENTER >/dev/null 2>&1; sleep 3
    i=0; while [ $i -lt 4 ]; do tap_primary; sleep 2; i=$((i + 1)); done
    i=0; while [ $i -lt 25 ]; do
        "$ADB" emu finger touch 7 >/dev/null 2>&1; sleep 1
        "$ADB" emu finger remove >/dev/null 2>&1
        i=$((i + 1))
    done
    sleep 1
    "$ADB" shell dumpsys window 2>/dev/null | grep -q 'FingerprintEnrollFinish' || {
        echo "FAIL could not complete a new enrollment (is a PIN set and one fingerprint present?)"
        exit 1
    }
    tap_primary; sleep 1
}

echo "1/4 · installing the instrumentation (no reinstall happens after this point)…"
./gradlew :composeApp:installAndroidDeviceTest -q || exit 1

echo "2/4 · phase A — create the key and confirm the OS lets it sign…"
phase phaseA_theFreshKeyIsUsableForSigning | grep -q '^OK' || {
    echo "FAIL phase A did not pass; the key could not be created or is already invalid."
    exit 1
}

echo "3/4 · CONTROL — phase B with NO new enrollment; it MUST fail…"
if phase phaseB_theSameKeyIsDestroyedByANewEnrollment | grep -q '^OK'; then
    echo "FAIL the control passed, which means phase B proves nothing:"
    echo "     the key was already gone before any enrollment (reinstall? wiped device?)."
    exit 1
fi

echo "4/4 · enrolling a new fingerprint, then phase B again — it MUST pass…"
enroll_one_more_fingerprint
phase phaseB_theSameKeyIsDestroyedByANewEnrollment | grep -q '^OK' || {
    echo "FAIL the key SURVIVED a new biometric enrollment."
    echo "     Someone who enrolls their own fingerprint could unlock the doctor's session."
    exit 1
}

echo ""
echo "tier2-invalidation: PROVEN on device — a new enrollment destroys the unlock key."
