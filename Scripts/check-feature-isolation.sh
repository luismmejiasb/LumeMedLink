#!/bin/sh
# Feature-isolation gate — the ADR-0008 tree contract, enforced on packages (mirror of LumeMed's
# folder gate and LumeUIComposer's lint-layout.sh).
#
# WHY A SCRIPT AND NOT detekt: detekt takes custom rules as compiled Kotlin classes, not YAML.
# Whether that module is ever worth writing is the author's open question; until then the gate
# lives here, depends on no external tool, and runs in CI all the same. A simple gate that exists
# beats an elegant one that does not.
#
# What it enforces (ADR-0008):
#   I1  A feature never imports another feature's area. Cross-area talk goes through core/.
#   I2  Direction is features → core → shared. core/ never imports features or app;
#       shared/ never imports core, features or app.
#   I3  expect/actual lives in core/ only. A screen must not know what platform it runs on.
#   I4  app/ is the composition root: nobody imports it.
#
# Usage: Scripts/check-feature-isolation.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

PKG='com\.luismejias\.lumemedlink'
KOTLIN_ROOT="composeApp/src"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    shift 2
    for line in "$@"; do echo "    $line"; done
}

# ── I1 · cross-feature imports ──────────────────────────────────────────────────────────────────
for f in $(find "$KOTLIN_ROOT" -path '*/features/*' -name '*.kt' 2>/dev/null); do
    area=$(echo "$f" | sed 's|.*/features/||' | cut -d/ -f1)
    hits=$(grep -nE "^import ${PKG}\.features\." "$f" | grep -vE "features\.${area}(\.|$)" || true)
    if [ -n "$hits" ]; then
        fail "I1 cross-feature import in features/${area}" \
             "A feature never imports another feature's area (ADR-0008). Promote to core/ instead." \
             "$f:" "$hits"
    fi
done

# ── I2 · dependency direction ───────────────────────────────────────────────────────────────────
# An empty dir list must SKIP the grep: `grep -r` with no paths scans the whole tree and reports
# other layers' legitimate imports as core's. (Caught by this script's own bait rehearsal.)
core_dirs=$(find "$KOTLIN_ROOT" -type d -path '*/core' 2>/dev/null)
if [ -n "$core_dirs" ]; then
    hits=$(grep -rnE "^import ${PKG}\.(features|app)\." $core_dirs 2>/dev/null || true)
    if [ -n "$hits" ]; then
        fail "I2 core/ imports upward" \
             "core/ never imports features or app (ADR-0008: features → core → shared)." "$hits"
    fi
fi
shared_dirs=$(find "$KOTLIN_ROOT" -type d -path '*/shared' 2>/dev/null)
if [ -n "$shared_dirs" ]; then
    hits=$(grep -rnE "^import ${PKG}\.(features|app|core)\." $shared_dirs 2>/dev/null || true)
    if [ -n "$hits" ]; then
        fail "I2 shared/ imports upward" \
             "shared/ is pure value types: it imports nothing above it (ADR-0008)." "$hits"
    fi
fi

# ── I3 · expect/actual outside core/ ────────────────────────────────────────────────────────────
hits=$(grep -rnE '\bexpect (fun|class|val|var|object|interface|enum)' "$KOTLIN_ROOT" 2>/dev/null | grep -v '/core/' || true)
if [ -n "$hits" ]; then
    fail "I3 expect declaration outside core/" \
         "Platform seams live in core/ only; a screen must not know its platform (ADR-0008)." "$hits"
fi
hits=$(grep -rnE '\bactual (fun|class|val|var|object|interface|enum)' "$KOTLIN_ROOT" 2>/dev/null | grep -v '/core/' || true)
if [ -n "$hits" ]; then
    fail "I3 actual declaration outside core/" \
         "Platform seams live in core/ only (ADR-0008)." "$hits"
fi

# ── I5 · no stray `core` directory ──────────────────────────────────────────────────────────────
# detekt's ForbiddenImport exempts core/ — the infrastructure edge. A directory named `core`
# anywhere else inherits that exemption and turns off the raw-networking and secrets gates for
# whatever is inside it. Only the canonical path may carry the name.
stray=$(find "$KOTLIN_ROOT" -type d -name core 2>/dev/null | grep -v "kotlin/com/luismejias/lumemedlink/core$" || true)
if [ -n "$stray" ]; then
    fail "I5 a directory named 'core' outside the canonical tree" \
         "It inherits detekt's core/ exemption, silently disabling no_raw_networking and secrets_gate there." "$stray"
fi

# ── I4 · nobody imports app/ ────────────────────────────────────────────────────────────────────
hits=$(grep -rnE "^import ${PKG}\.app\." "$KOTLIN_ROOT" 2>/dev/null | grep -vE '/(app)/' || true)
if [ -n "$hits" ]; then
    fail "I4 app/ imported from below" \
         "app/ is the composition root; features, core and shared never import it (ADR-0008)." "$hits"
fi

if [ $FAIL -eq 0 ]; then
    echo "feature-isolation: OK"
else
    exit 1
fi
