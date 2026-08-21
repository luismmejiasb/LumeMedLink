#!/bin/sh
# Content gates the constitution names and detekt cannot carry syntactically (§9, §13).
#
# These are grep-shaped ON PURPOSE. detekt's call-site rules (ForbiddenMethodCall) require type
# resolution, and the plain `detekt` task never resolves types — the rule stays green forever, a
# dead gate. A grep is cruder (it will flag the word in a comment) and that is the acceptable
# price: a false red gets rewritten; a false green ships a violation. Each pattern was rehearsed
# against a bait file before being trusted (family rule: a gate that was never seen red does not
# exist).
#
# What it hunts:
#   P1  no_globalscope — total, no exceptions, core/ included (§6).
#   P2  no_document_delivery — ADR-0007: share sheets / exporters have no legitimate clinical use
#       in this app. Total, no exceptions: FileProvider, ACTION_SEND*, UIActivityViewController.
#   P3  secrets_gate, call-shaped half — plain-storage CALLS outside core/ (§8.4, ADR-0005). The
#       import-shaped half lives in detekt's ForbiddenImport.
#
# Usage: Scripts/check-forbidden-patterns.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

SRC="composeApp/src androidApp/src"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    echo "$3" | sed 's/^/    /'
}

# ── P1 · no_globalscope (§6) ────────────────────────────────────────────────────────────────────
hits=$(grep -rn 'GlobalScope' $SRC 2>/dev/null || true)
if [ -n "$hits" ]; then
    fail "P1 GlobalScope" \
         "Structured concurrency only: every coroutine has an owning scope (§6). No exceptions, core/ included." \
         "$hits"
fi

# ── P2 · no_document_delivery (ADR-0007 / backend trap T1) ──────────────────────────────────────
hits=$(grep -rnE 'FileProvider|ACTION_SEND|UIActivityViewController|ShareCompat' $SRC 2>/dev/null || true)
if [ -n "$hits" ]; then
    fail "P2 document delivery surface" \
         "This app never shows nor transports documents (ADR-0007). A share sheet here reopens Ley 19.799." \
         "$hits"
fi

# ── P3 · plain-storage calls outside core/ (§8.4, ADR-0005) ─────────────────────────────────────
hits=$(grep -rnE 'getSharedPreferences|NSUserDefaults|UserDefaults\.' $SRC 2>/dev/null | grep -v '/core/' || true)
if [ -n "$hits" ]; then
    fail "P3 plain storage call outside core/" \
         "Secrets live in Keystore/Keychain; any other persistence is a core/ seam with its ADR (§8.4, ADR-0005)." \
         "$hits"
fi

if [ $FAIL -eq 0 ]; then
    echo "forbidden-patterns: OK"
else
    exit 1
fi
