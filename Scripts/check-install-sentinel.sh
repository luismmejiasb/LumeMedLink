#!/bin/sh
# Install sentinel gate (F7, ADR-0028) — the guard that stops a reinstall inheriting the previous
# installation's secrets.
#
# On iOS the Keychain SURVIVES the app being deleted while the container does not. Measured on a
# real runtime with a live control that reproduces the hole when the guard is removed
# (Scripts/verify-install-sentinel.sh). This gate keeps the pieces that make it work from rotting.
#
# Rehearsed against bait before being trusted.
#
# Usage: Scripts/check-install-sentinel.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

PROBE="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/SessionProbe.kt"
IOS="composeApp/src/iosMain/kotlin/com/luismejias/lumemedlink/core/session/PlatformInstallSentinel.ios.kt"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
}

# Comments AND imports are stripped. The import alone satisfied every presence check in the first
# draft of this gate — its own bait rehearsal caught five green failures in a row, which is the same
# trap FLAG_SECURE's gate fell into three times: a MENTION is not the mechanism.
code() { grep -vE '^[[:space:]]*(//|\*|/\*|import )' "$1" 2>/dev/null; }

# ── The guard must be ON the launch path, and BEFORE the session is looked for ───────────────────
if ! code "$PROBE" | grep -q 'enforceInstallBoundary('; then
    fail "install-sentinel: the launch probe does not enforce the install boundary" \
         "A guard nothing calls is decoration. probeSession must run it (ADR-0028)."
else
    boundary_line=$(code "$PROBE" | grep -n 'enforceInstallBoundary(' | head -1 | cut -d: -f1)
    session_line=$(code "$PROBE" | grep -n 'sessionManager.hasSession()' | head -1 | cut -d: -f1)
    if [ -n "$session_line" ] && [ "$boundary_line" -gt "$session_line" ]; then
        fail "install-sentinel: the session is read BEFORE the boundary runs" \
             "Reading first finds the PREVIOUS installation session and answers yes. Purging after that is too late (ADR-0028)."
    fi
fi

# ── The three properties of the marker file, each load-bearing ──────────────────────────────────
if ! code "$IOS" | grep -q 'NSFileProtectionNone'; then
    fail "install-sentinel: the marker is not readable before first unlock" \
         "A protected marker cannot be read by a background launch on a locked phone, which then concludes fresh-install and purges a VALID session — the documented mass-logout failure of this pattern (ADR-0028)."
fi
if ! code "$IOS" | grep -q 'NSURLIsExcludedFromBackupKey'; then
    fail "install-sentinel: the marker is not excluded from backup" \
         "A restore would land WITH the marker and skip the purge, leaving restored Keychain residue in place (ADR-0028, section 8.5)."
fi
if ! code "$IOS" | grep -q 'NSApplicationSupportDirectory'; then
    fail "install-sentinel: the marker does not live in Application Support" \
         "Caches can be evicted under disk pressure, and an evicted marker reads as a fresh install and logs the doctor out for nothing. Documents is user-visible surface this app has no business writing to (ADR-0028)."
fi

# ── The purge is OURS only. This code is in the perfect position to break that rule ──────────────
hits=$(grep -rn 'kSecAttrSynchronizableAny\|kSecClassInternetPassword\|kSecClassCertificate\|kSecClassKey\|kSecClassIdentity' composeApp/src 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' || true)
if [ -n "$hits" ]; then
    fail "install-sentinel: a sweep beyond this app own Keychain service" \
         "Purging other software entries is vandalism, and a synchronizable sweep propagates the deletion to the user OTHER devices through iCloud Keychain (ADR-0005, ADR-0028)."
    echo "$hits" | sed 's/^/    /'
fi

if [ $FAIL -eq 0 ]; then
    echo "install-sentinel: OK"
else
    exit 1
fi
