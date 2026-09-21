#!/bin/sh
# The logout contract (F5, ADR-0014 as amended 2026-09-21).
#
# WHY THIS GATE EXISTS: for three weeks ADR-0014, ADR-0009 and ADR-0022 all described a logout that
# erased the tier-1 key, and the code unlinked ONE file and never touched the key. Two tests were
# green over it — one asserted on `wipe()`, a method no production path called, and the other took
# the real path but asserted neither the files nor the alias, though its name promised both.
#
# Nothing a grep can see would have caught that, and this gate does not pretend otherwise: what it
# checks is the SHAPE that made the defect possible — a logout that erases by key instead of by
# namespace, a step that is declared and never attempted, a cancellable erase, and a shell that
# re-inlines the sequence instead of calling the contract. The behaviour itself is pinned by
# LogoutContractTest and by LogoutWipeOnDeviceTest on real hardware.
#
# Asserts per ADR-0029: the call, with comments and string literals removed by tokenizer.
#
# Rehearsed against bait (Scripts/rehearse-gates.sh).
#
# Usage: Scripts/check-logout-contract.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

CONTRACT="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session/LogoutContract.kt"
SHELL_FILE="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/app/App.kt"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

for f in "$CONTRACT" "$SHELL_FILE"; do
    [ -f "$f" ] || { echo "FAIL logout: $f is missing — the contract has no home"; exit 1; }
done

code() { python3 Scripts/lib/uncomment.py --lang c --strip-strings --flatten "$1" 2>/dev/null; }
lines() { python3 Scripts/lib/uncomment.py --lang c --strip-strings "$1" 2>/dev/null; }

CONTRACT_CODE=$(code "$CONTRACT")
SHELL_CODE=$(code "$SHELL_FILE")

# ── 1. EVERY declared step is attempted ─────────────────────────────────────────────────────────
# Driven by the enum, exactly like SecureStoreWipeTest: a step added by a future slice is covered
# the moment it is declared, and nobody has to remember to extend this gate.
STEPS=$(lines "$CONTRACT" | sed -n '/enum class LogoutStep/,/^}/p' | sed -n 's/^[[:space:]]*\([A-Z][A-Z0-9_]*\),.*/\1/p')
[ -n "$STEPS" ] || fail "logout: no LogoutStep values found" "The enum drives this gate; if it cannot be read, the gate proves nothing."
for s in $STEPS; do
    printf '%s\n' "$CONTRACT_CODE" | grep -qE "step\([[:space:]]*LogoutStep\.$s[[:space:]]*\)" ||
        fail "logout: LogoutStep.$s is declared but never attempted" \
             "Every step of ADR-0014 must be attempted by performLogout, and attempted even after an earlier one throws."
done

# ── 2. The namespace, not one key ───────────────────────────────────────────────────────────────
# THE defect this gate is named after. `remove(...)` erases one entry and leaves the tier-1 key
# alive, so any stray copy of the ciphertext stays decryptable on that hardware — which is exactly
# what ADR-0014 point 3 claims to close.
printf '%s\n' "$CONTRACT_CODE" | grep -qE 'secureStore\.wipe\(\)' ||
    fail "logout: the erase does not call secureStore.wipe()" \
         "Logout erases the whole namespace AND the key (ADR-0014 point 3). Erasing by key leaves the key."
if printf '%s\n' "$CONTRACT_CODE" | grep -qE 'secureStore\.remove\('; then
    fail "logout: the erase removes a single entry" \
         "A key-by-key erase is the shape that let UNLOCK_CHALLENGE and the tier-1 key survive a logout."
fi

# ── 3. A cancelled caller must not truncate the erase ───────────────────────────────────────────
printf '%s\n' "$CONTRACT_CODE" | grep -qE 'withContext\([[:space:]]*NonCancellable[[:space:]]*\)' ||
    fail "logout: the erase is cancellable" \
         "It ran in the shell's rememberCoroutineScope(), which dies with the composition — backgrounding the app mid-logout truncated the wipe (ADR-0014)."

# ── 4. The shell calls the contract, and does not re-inline it ──────────────────────────────────
printf '%s\n' "$SHELL_CODE" | grep -qE 'performLogout\(' ||
    fail "logout: the shell does not call performLogout()" \
         "The contract exists so every branch of it is reachable by a test; a shell that erases inline is the shape that was wrong."
for reinlined in 'sessionManager\.logout\(\)' 'unlockGate\.clear\(\)' 'sessionLock\.sessionEnded\(\)'; do
    if printf '%s\n' "$SHELL_CODE" | grep -qE "$reinlined"; then
        fail "logout: the shell calls part of the erase directly" \
             "Re-inlining a step means it can be reordered or skipped without a test noticing." "$reinlined"
    fi
done

if [ $FAIL -eq 0 ]; then
    echo "logout-contract: OK ($(printf '%s\n' "$STEPS" | wc -l | tr -d ' ') steps, all attempted)"
else
    exit 1
fi
