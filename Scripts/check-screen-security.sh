#!/bin/sh
# Screen-capture protection gate (F1, ADR-0010) — so the app-wide screenshot decision cannot rot.
#
# Two shapes, both needed (a "must be present" control decays silently if only "must not appear" is
# gated):
#   PRESENCE  FLAG_SECURE and filterTouchesWhenObscured must exist in the Android shell.
#   ABSENCE   nobody clears FLAG_SECURE, and no screen-capture API appears in app code.
#
# Rehearsed against bait before being trusted (family rule): removing the flag from MainActivity,
# and adding a PixelCopy call, must both turn this red.
#
# Usage: Scripts/check-screen-security.sh   (exit non-zero on violations)

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
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# ── PRESENCE ────────────────────────────────────────────────────────────────────────────────────
# Comment mentions do NOT count: the token must survive in a non-comment line, or deleting the
# real call while leaving the doc comment would pass green. (This exact hole was caught by the
# bait rehearsal — the comment says "FLAG_SECURE app-wide" and fooled a plain grep.)
# .kt only: a mention in the XML manifest comment (`<!-- … -->`) is prose, not the mechanism — and
# the bait rehearsal caught exactly that (the manifest's "FLAG_SECURE lands with…" note passed a
# grep that scanned all files). Kotlin `//` comment lines are stripped too.
present_in_code() {
    grep -rh --include='*.kt' "$1" androidApp/src 2>/dev/null | grep -vqE '^[[:space:]]*(//|\*|/\*)'
}
if ! present_in_code 'FLAG_SECURE'; then
    fail "screen-security: FLAG_SECURE missing from the Android shell" \
         "ADR-0010 makes screenshot protection app-wide; the Activity must set FLAG_SECURE."
fi
if ! present_in_code 'filterTouchesWhenObscured'; then
    fail "screen-security: tapjacking guard missing" \
         "ADR-0010: the Android shell must set filterTouchesWhenObscured on its window."
fi

# ── ABSENCE ─────────────────────────────────────────────────────────────────────────────────────
hits=$(grep -rnE 'clearFlags\([^)]*FLAG_SECURE' $SRC 2>/dev/null || true)
if [ -n "$hits" ]; then
    fail "screen-security: FLAG_SECURE is being cleared" \
         "Nothing may drop the app-wide screenshot protection (ADR-0010)." "$hits"
fi
# Screen-capture surfaces the app itself must never open. (Reading its OWN screen is exactly how a
# leak is built; the OS snapshot is already covered by FLAG_SECURE + the privacy cover.)
hits=$(grep -rnE 'PixelCopy|MediaProjection|createScreenCaptureIntent|imageContentsOfScreen' $SRC 2>/dev/null || true)
if [ -n "$hits" ]; then
    fail "screen-security: screen-capture API in app code" \
         "This app never captures its own screen (ADR-0010 / threat model T2)." "$hits"
fi

if [ $FAIL -eq 0 ]; then
    echo "screen-security: OK"
else
    exit 1
fi
