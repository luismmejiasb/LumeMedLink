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

# Non-comment occurrences only: a mention inside a KDoc block explaining the rule must never be
# mistaken for the rule being applied. (This exact hole sank the first version of the screen gate.)
code_lines() {
    grep -h "$2" "$1" 2>/dev/null | grep -vE '^[[:space:]]*(//|\*|/\*)'
}
require() {
    if [ ! -f "$1" ]; then
        fail "biometric-contract: $1 is missing" "The tier-2 gate must exist (ADR-0011)."
        return
    fi
    if [ -z "$(code_lines "$1" "$2")" ]; then
        fail "biometric-contract: $3" "$4"
    fi
}

# ── ANDROID · the three parameters ADR-0005 makes contract ──────────────────────────────────────
require "$ANDROID_GATE" 'setUserAuthenticationRequired(true)' \
    "the tier-2 key does not require user authentication" \
    "Without it the key is usable with no biometric at all — the gate becomes decoration (ADR-0005)."

require "$ANDROID_GATE" 'setInvalidatedByBiometricEnrollment(true)' \
    "the tier-2 key survives a new biometric enrollment" \
    "Whoever holds the phone could enroll their own finger and inherit the doctor's session (ADR-0005)."

require "$ANDROID_GATE" 'AUTH_BIOMETRIC_STRONG' \
    "the tier-2 key does not demand STRONG biometrics" \
    "Weak (class 2) biometrics do not carry the invalidation guarantee this tier is paid for."

require "$ANDROID_GATE" 'BIOMETRIC_STRONG' \
    "the prompt does not restrict itself to strong biometrics" \
    "setAllowedAuthenticators must be BIOMETRIC_STRONG (ADR-0005)."

# ── ANDROID · what must never appear ────────────────────────────────────────────────────────────
# A POSITIVE validity duration turns per-use authentication into a time window and, with it,
# silently voids setInvalidatedByBiometricEnrollment. -1 is the pre-API-30 spelling of per-use and
# is the ONLY accepted argument.
hits=$(grep -rn 'setUserAuthenticationValidityDurationSeconds([^-]' $SRC 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*)' || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: a validity TIME WINDOW replaced per-use authentication" \
         "Only setUserAuthenticationValidityDurationSeconds(-1) (per use) is allowed; a positive window voids invalidation-on-enrollment." \
         "$hits"
fi

hits=$(grep -rn 'DEVICE_CREDENTIAL' $SRC 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*)' || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: device-credential fallback present" \
         "A PIN/pattern fallback both weakens the gate and voids the enrollment-invalidation property (ADR-0005)." \
         "$hits"
fi

hits=$(grep -rn 'setInvalidatedByBiometricEnrollment(false)' $SRC 2>/dev/null || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: enrollment invalidation explicitly disabled" \
         "This is the property the tier exists for (ADR-0005)." "$hits"
fi

# ── iOS · the access control flag is contract ───────────────────────────────────────────────────
require "$IOS_GATE" 'kSecAccessControlBiometryCurrentSet' \
    "the iOS tier-2 item is not pinned to the CURRENT biometric set" \
    "Only .biometryCurrentSet destroys the secret when enrollment changes (ADR-0005)."

require "$IOS_GATE" 'kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly' \
    "the iOS tier-2 item lost its passcode floor" \
    "The accessibility class travels inside the access control object and must stay at the ADR-0005 floor."

# Weaker ACL flags that would still compile and still show a prompt, while accepting a PIN or any
# enrolled biometric — the exact silent downgrades this gate exists to catch.
hits=$(grep -rn 'kSecAccessControlBiometryAny\|kSecAccessControlUserPresence\|kSecAccessControlDevicePasscode\|kSecAccessControlOr' $SRC 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*)' || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: a weaker iOS access-control flag is in use" \
         "BiometryAny/UserPresence/DevicePasscode accept a changed enrollment or a passcode — not this tier (ADR-0005)." \
         "$hits"
fi

# ── The anti-boolean rule, as far as a script can see it ────────────────────────────────────────
# LAContext.evaluatePolicy answers "did they authenticate?" with a boolean, which is the pattern
# ADR-0005 forbids: unlocking must be the recovery of key material. canEvaluatePolicy (a
# capability question) is fine and is what the gate uses.
hits=$(grep -rn 'evaluatePolicy(' $SRC 2>/dev/null | grep -v 'canEvaluatePolicy' | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*)' || true)
if [ -n "$hits" ]; then
    fail "biometric-contract: boolean biometric check (evaluatePolicy) in use" \
         "Unlock must be anchored to key material the OS releases, never to a boolean (ADR-0005)." \
         "$hits"
fi

if [ $FAIL -eq 0 ]; then
    echo "biometric-contract: OK"
else
    exit 1
fi
