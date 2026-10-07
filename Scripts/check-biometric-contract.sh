#!/bin/sh
# Tier-2 biometric gate contract (F4, ADR-0005 + ADR-0011).
#
# WHY THIS GATE EXISTS AT ALL: ADR-0005 says the tier-2 key parameters are not an implementation
# choice — "changing them changes the security claim, and needs this ADR reopened". A sentence in a
# document cannot enforce that. Every parameter below can be weakened by a one-word edit that still
# compiles, still runs, still shows a fingerprint prompt, and silently gives up the property the
# tier is paid for. This script is what makes that edit fail the build instead of shipping.
#
# Rehearsed against bait before being trusted: each PRESENCE line removed, and each ABSENCE pattern
# added, must turn this red.
#
# Usage: Scripts/check-biometric-contract.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

ANDROID_GATE="composeApp/src/androidMain/kotlin/com/luismejias/lumemedlink/core/session/BiometricUnlockGate.kt"
IOS_GATE="composeApp/src/iosMain/kotlin/com/luismejias/lumemedlink/core/session/KeychainUnlockGate.kt"
SRC="composeApp/src androidApp/src"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# Asserted where the key is MADE, with comments and string literals blanked by the tokenizer
# (ADR-0029). This half used to filter comments line by line — the filter ADR-0029 forbids — and a
# required parameter moved into a `/* */` block, or turned into a trailing comment like
# `0u, // was kSecAccessControlBiometryCurrentSet`, kept it green with the key signing on no
# biometric at all (task 0002, F03, reproduced). Now the words count only inside the function that
# builds the key, as code.
body_of() { python3 Scripts/lib/kfun.py "$1" "$2" 2>/dev/null; }
require_in() {
    # require_in FILE FUNCTION LITERAL MESSAGE REASON
    if [ ! -f "$1" ]; then
        fail "biometric-contract: $1 is missing" "The tier-2 gate must exist (ADR-0011)."
        return
    fi
    body_of "$1" "$2" | grep -qF "$3" || fail "biometric-contract: $4" "$5"
}
# Every non-comment, non-string line of the source tree matching PATTERN, as file:line:text.
stripped_hits() {
    for f in $(find $SRC -name '*.kt' -type f 2>/dev/null); do
        python3 Scripts/lib/uncomment.py --lang c --strip-strings "$f" 2>/dev/null |
            grep -nE "$1" | sed "s|^|$f:|"
    done
}

# ── ANDROID · the three parameters ADR-0005 makes contract, in the function that makes the key ───
require_in "$ANDROID_GATE" generateUnlockKeyPair 'setUserAuthenticationRequired(true)' \
    "the tier-2 key does not require user authentication" \
    "Without it the key is usable with no biometric at all — the gate becomes decoration (ADR-0005)."
require_in "$ANDROID_GATE" generateUnlockKeyPair 'setInvalidatedByBiometricEnrollment(true)' \
    "the tier-2 key survives a new biometric enrollment" \
    "Whoever holds the phone could enroll their own finger and inherit the doctor's session (ADR-0005)."
require_in "$ANDROID_GATE" generateUnlockKeyPair 'AUTH_BIOMETRIC_STRONG' \
    "the tier-2 key does not demand STRONG biometrics" \
    "Weak (class 2) biometrics do not carry the invalidation guarantee this tier is paid for."

# THE CALL, not the token — `BIOMETRIC_STRONG` as a bare substring is satisfied by
# `AUTH_BIOMETRIC_STRONG` (audit, ADR-0029), so the prompt's call is asserted with its argument.
if [ -f "$ANDROID_GATE" ] &&
    ! body_of "$ANDROID_GATE" promptForSignature | grep -qE 'setAllowedAuthenticators\( ?BiometricManager\.Authenticators\.BIOMETRIC_STRONG ?\)'; then
    fail "biometric-contract: the prompt does not restrict itself to strong biometrics" \
         "setAllowedAuthenticators must be called with BIOMETRIC_STRONG alone (ADR-0005). A PIN fallback would be re-entry without the key material this tier is built on."
fi

# ── ANDROID · what must never appear ────────────────────────────────────────────────────────────
# A POSITIVE validity duration turns per-use authentication into a time window and, with it,
# silently voids setInvalidatedByBiometricEnrollment. -1 is the pre-API-30 spelling of per-use.
hits=$(stripped_hits 'setUserAuthenticationValidityDurationSeconds\([^-]' || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: a validity TIME WINDOW replaced per-use authentication" \
         "Only setUserAuthenticationValidityDurationSeconds(-1) (per use) is allowed; a positive window voids invalidation-on-enrollment." \
         "$hits"
fi
hits=$(stripped_hits 'DEVICE_CREDENTIAL' || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: device-credential fallback present" \
         "A PIN/pattern fallback both weakens the gate and voids the enrollment-invalidation property (ADR-0005)." \
         "$hits"
fi
hits=$(stripped_hits 'setInvalidatedByBiometricEnrollment\(false\)' || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: enrollment invalidation explicitly disabled" \
         "This is the property the tier exists for (ADR-0005)." "$hits"
fi

# ── iOS · the access control flag is contract, in the function that creates the item ───────────
require_in "$IOS_GATE" enroll 'kSecAccessControlBiometryCurrentSet' \
    "the iOS tier-2 item is not pinned to the CURRENT biometric set" \
    "Only .biometryCurrentSet destroys the secret when enrollment changes (ADR-0005)."
require_in "$IOS_GATE" enroll 'kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly' \
    "the iOS tier-2 item lost its passcode floor" \
    "The accessibility class travels inside the access control object and must stay at the ADR-0005 floor."

# The prompting read runs OFF the main thread (task 0006): SecItemCopyMatching blocks the thread
# that calls it for as long as Face ID is up, and Apple says not to call it from main.
body_of "$IOS_GATE" unlock | grep -qE 'withContext\( ?ioDispatcher ?\) ?\{ ?readUnlockSecret\(\) ?\}' ||
    fail "biometric-contract: the iOS unlock does not leave the main thread" \
         "unlock() must run the Keychain read through the injected ioDispatcher (task 0006)."

# Weaker ACL flags that would still compile and still show a prompt, while accepting a PIN or any
# enrolled biometric — the exact silent downgrades this gate exists to catch.
hits=$(stripped_hits 'kSecAccessControlBiometryAny|kSecAccessControlUserPresence|kSecAccessControlDevicePasscode|kSecAccessControlOr' || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: a weaker iOS access-control flag is in use" \
         "BiometryAny/UserPresence/DevicePasscode accept a changed enrollment or a passcode — not this tier (ADR-0005)." \
         "$hits"
fi

# ── The anti-boolean rule, as far as a script can see it ────────────────────────────────────────
# LAContext.evaluatePolicy answers "did they authenticate?" with a boolean, which is the pattern
# ADR-0005 forbids: unlocking must be the recovery of key material. canEvaluatePolicy (a
# capability question) is fine and is what the gate uses.
hits=$(stripped_hits 'evaluatePolicy\(' | grep -v 'canEvaluatePolicy' || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: boolean biometric check (evaluatePolicy) in use" \
         "Unlock must be anchored to key material the OS releases, never to a boolean (ADR-0005)." \
         "$hits"
fi

# ── The inactivity window: what MEASURES it, and what WAKES UP for it (F4, ADR-0032) ────────────
# Two defects, one slice, both found by audit and both invisible to every gate that existed:
#
#  1. The window was measured with the WALL CLOCK alone, and the wall clock is a setting. Move the
#     phone's time backwards and `now - last` goes negative: the window never elapses and the
#     session never locks. A lock anyone holding the device can switch off in Settings, on the
#     shared-device threat this app ranks FIRST (§8.17).
#  2. NOTHING re-read the lock except the pointer handler, so the window could elapse with the
#     agenda on screen and the app would only notice when somebody touched it — the one moment the
#     person is already looking at the screen. A phone left on a table has no touches in it.
#
# Asserted as calls with comments and string literals stripped (ADR-0029).
LOCK_SRC="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session/InactivityLock.kt"
SHELL_SRC="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/App.kt"
lock_code() { python3 Scripts/lib/uncomment.py --lang c --strip-strings --flatten "$1" 2>/dev/null; }

if [ ! -f "$LOCK_SRC" ] || [ ! -f "$SHELL_SRC" ]; then
    fail "biometric: the inactivity lock or the shell is missing" "Nothing below proved anything."
else
    LOCK_CODE=$(lock_code "$LOCK_SRC")
    SHELL_CODE=$(lock_code "$SHELL_SRC")

    printf '%s\n' "$LOCK_CODE" | grep -qE 'clock\.nowEpochMillis\(\)' ||
        fail "biometric: the inactivity window does not read the wall clock" \
             "It is one of the two sources, and it is the one that covers an elapsed clock paused by device sleep (ADR-0032)."
    printf '%s\n' "$LOCK_CODE" | grep -qE 'elapsedClock\.elapsedMillis\(\)' ||
        fail "biometric: the inactivity window does not read an unmovable elapsed clock" \
             "Measured by the wall clock alone, the window is switched off by moving the phone's time backwards (ADR-0032)."
    printf '%s\n' "$LOCK_CODE" | grep -qE 'maxOf\([[:space:]]*byWallClock[[:space:]]*,[[:space:]]*byElapsedClock[[:space:]]*\)' ||
        fail "biometric: the window does not close when EITHER clock says it elapsed" \
             "Taking the larger of the two elapsed values is what is fail-closed against both failures: a wall clock the user moves, and an elapsed clock that pauses in sleep (ADR-0032)."

    printf '%s\n' "$SHELL_CODE" | grep -qE 'sessionLock\.millisUntilLock\(\)' ||
        fail "biometric: the shell never asks how long is left on the window" \
             "Without it the lock only fires on the next touch, and a phone left on a table has no touches in it (ADR-0032)."
    printf '%s\n' "$SHELL_CODE" | grep -qE 'delay\([[:space:]]*remaining[[:space:]]*\)' ||
        fail "biometric: the shell does not sleep until the window closes" \
             "The window must close on its own, not when someone happens to touch the screen (ADR-0032)."
    # The timer sleeps on a clock that stops while the device sleeps, so coming back must re-ask the
    # lock before the cover drops (task 0002, F01). Without this, a phone that slept for an hour
    # returns with the agenda on screen.
    printf '%s\n' "$SHELL_CODE" | tr '\n' ' ' | grep -qE 'Lifecycle\.Event\.ON_START\)[[:space:]]*\{[[:space:]]*locked[[:space:]]*=[[:space:]]*sessionLock\.isLocked\(\)' ||
        fail "biometric: coming back to the app does not re-ask the lock" \
             "The timer's clock stops in sleep; ON_START must re-read sessionLock.isLocked() while the cover is still up (ADR-0032, amended 2026-10-07)."
fi

if [ $FAIL -eq 0 ]; then
    echo "biometric-contract: OK"
else
    exit 1
fi
