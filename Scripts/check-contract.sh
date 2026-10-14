#!/bin/sh
# The contract's client is what the vendored contract and the allowlist say, and nothing else (ADR-0061).
#
#   C1  contract/openapi.json is the file contract/PIN names: byte-identical to the platform's, by SHA-256.
#   C2  the generated client is what Scripts/generate-contract.py writes today (no drift, no hand edit). The generator
#       itself refuses an operation outside /v1/patient, /v1/directory, /v1/reference and /v1/me, and a schema
#       construct it does not understand, so a refusal fails here too.
#   C3  no route is written by hand: a "v1/…" string, with or without its leading slash, in shipped code outside the
#       generated operations is red. The slash is optional because the stack takes routes RELATIVE to its base URL,
#       and the one hand-written route this repo had ("v1/security-events") had none — a first draft of this check
#       demanded the slash and passed over it. A comment naming a route is documentation, not a call, so comments
#       are blanked by the tokenizer, never filtered by line (ADR-0029).
#
# Usage: Scripts/check-contract.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

CONTRACT="contract/openapi.json"
PIN="contract/PIN"
GENERATED="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/networking/contract"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# ── C1 · the vendored contract is the pinned one ─────────────────────────────────────────────────
expected=$(sed -n 's/^sha256=//p' "$PIN" 2>/dev/null)
actual=$(shasum -a 256 "$CONTRACT" 2>/dev/null | cut -d' ' -f1)
if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
    fail "C1 the vendored contract is not the pinned one" \
         "contract/openapi.json must be the platform's file, byte-identical, with its hash in contract/PIN (ADR-0061)." \
         "pinned: ${expected:-none}
actual: ${actual:-missing}"
fi

# ── C2 · the generated client has no drift ───────────────────────────────────────────────────────
mkdir -p tmp
fresh=$(mktemp -d "$REPO_ROOT/tmp/contract-check.XXXXXX")
trap 'rm -rf "$fresh"' EXIT
if ! refusal=$(python3 Scripts/generate-contract.py --out "$fresh" 2>&1); then
    fail "C2 the generator refused the contract or the allowlist" \
         "Only /v1/patient, /v1/directory, /v1/reference and /v1/me are generated, and only constructs it understands." \
         "$refusal"
else
    for name in ContractModels.kt ContractOperations.kt; do
        if ! cmp -s "$fresh/$name" "$GENERATED/$name"; then
            fail "C2 $name differs from a fresh run" \
                 "Generated code is never edited by hand: change contract/operations.txt or the generator, and run it." \
                 "$(diff "$fresh/$name" "$GENERATED/$name" 2>&1 | head -20)"
        fi
    done
fi

# ── C3 · no route written by hand ────────────────────────────────────────────────────────────────
hits=""
for f in $(find composeApp/src androidApp/src -path '*Test*' -prune -o -name '*.kt' -print 2>/dev/null); do
    [ "$f" = "$GENERATED/ContractOperations.kt" ] && continue
    found=$(python3 Scripts/lib/uncomment.py --lang c "$f" 2>/dev/null | grep -nE '"/?v1/' || true)
    [ -n "$found" ] && hits="$hits
$f: $found"
done
if [ -n "$hits" ]; then
    fail "C3 a route written by hand" \
         "Every call goes through ContractOperations, generated from the allowlist (ADR-0061)." \
         "$hits"
fi

if [ $FAIL -eq 0 ]; then
    echo "contract: OK"
else
    exit 1
fi
