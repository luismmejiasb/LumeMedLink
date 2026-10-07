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

# The window that has input focus right now — the only cheap way to know which wizard screen is up.
focus() { "$ADB" shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus'; }

# Polls until the focused window matches $1, for at most $2 seconds.
wait_focus() {
    t=0
    while [ $t -lt "$2" ]; do
        focus | grep -q "$1" && return 0
        sleep 1; t=$((t + 1))
    done
    return 1
}

# How many fingerprints the device has enrolled. The postcondition of an enrollment is that this
# went UP — a screen name is a proxy, the count is the fact.
fingerprint_count() { "$ADB" shell dumpsys fingerprint 2>/dev/null | sed -n 's/.*"count":\([0-9]*\).*/\1/p' | head -1; }

# Drives the system wizard by WAITING for each screen, not by sleeping a fixed time. The sleep-driven
# version failed on 2026-10-07 on a loaded Mac (an iOS build was running beside it): the taps landed
# on the launcher's dock — the run ended with the Play Store in front — and it reported only "could
# not complete a new enrollment". The same steps, each waiting for its screen, enrolled at the third
# touch. Whether the wizard was late or something else went wrong was not isolated; this version
# does not need to know, and a failure now says which screen never came.
enroll_one_more_fingerprint() {
    before=$(fingerprint_count)
    "$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
    # A CLEAN task every time: a wizard left half-way by an earlier run (stuck on its capture screen)
    # is a task Android would rather bring back than restart.
    started=$("$ADB" shell am start -W --activity-clear-task -a android.settings.FINGERPRINT_ENROLL 2>&1)
    wait_focus 'ConfirmLock' 30 || {
        echo "FAIL the wizard never asked for the PIN (focus: $(focus))"
        echo "$started" | sed 's/^/     am start: /'
        exit 1
    }
    "$ADB" shell input text "$PIN" >/dev/null 2>&1
    "$ADB" shell input keyevent KEYCODE_ENTER >/dev/null 2>&1
    wait_focus 'FingerprintEnroll' 30 || { echo "FAIL the PIN did not open the wizard (focus: $(focus))"; exit 1; }
    # The intro scrolls in steps ("More", "More", "I agree") before the sensor screen.
    i=0
    while [ $i -lt 6 ] && ! focus | grep -q 'FingerprintEnrollFindSensor\|FingerprintEnrollEnrolling'; do
        tap_primary; sleep 2; i=$((i + 1))
    done
    focus | grep -q 'FingerprintEnrollFindSensor\|FingerprintEnrollEnrolling' ||
        { echo "FAIL the wizard never reached the sensor screen (focus: $(focus))"; exit 1; }
    # Touch, lift, and LOOK after every touch, stopping at the finish screen.
    #
    # A NEW virtual finger every run. This used a fixed id (7), and an id that is already enrolled
    # never progresses: the wizard sits on the capture screen and the count does not move — measured
    # on 2026-10-07 after a manual enrollment had used 7 first. Each run adds a print anyway, so each
    # run needs a finger the device has not seen.
    finger=$(( 1000 + $(date +%s) % 9000 ))
    i=0
    while [ $i -lt 25 ] && ! focus | grep -q 'FingerprintEnrollFinish'; do
        "$ADB" emu finger touch "$finger" >/dev/null 2>&1; sleep 1
        "$ADB" emu finger remove >/dev/null 2>&1; sleep 1
        i=$((i + 1))
    done
    after=$(fingerprint_count)
    if ! focus | grep -q 'FingerprintEnrollFinish' || [ "${after:-0}" -le "${before:-0}" ]; then
        echo "FAIL could not complete a new enrollment (enrolled before=$before after=$after; focus: $(focus))"
        echo "     That is the INSTRUMENT failing, not the property. (An emulator holds at most five"
        echo "     fingerprints, and every run of this script adds one.)"
        exit 1
    fi
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
