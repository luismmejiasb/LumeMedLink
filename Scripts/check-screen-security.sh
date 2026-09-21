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
# PRESENCE, and the three things that can carry a token without being the mechanism are all
# removed before the match (ADR-0029):
#   · the PATH is androidApp/src/main — a token in src/test or src/androidTest ships nothing, and a
#     dead `listOf("FLAG_SECURE", …)` in a test file passed the tree-wide grep this replaces;
#   · COMMENTS and STRING LITERALS are blanked by a tokenizer, so neither a KDoc naming the API nor
#     `val doc = "window.decorView.denyAutofillExport()"` counts;
#   · the pattern is the CALL, not the token, so an `import` of the same name cannot satisfy it.
# Flattened because the formatter wraps `window.setFlags(` across three lines.
SHELL_MAIN="androidApp/src/main"
SHELL_CODE=$(find "$SHELL_MAIN" -name '*.kt' -print0 2>/dev/null |
    xargs -0 python3 Scripts/lib/uncomment.py --lang c --strip-strings --flatten 2>/dev/null)
calls() { printf '%s\n' "$SHELL_CODE" | grep -qE "$1"; }

if ! calls 'window\.setFlags\([^)]*FLAG_SECURE'; then
    fail "screen-security: the shell does not CALL window.setFlags(..FLAG_SECURE..)" \
         "ADR-0010 makes screenshot protection app-wide; the Activity must set FLAG_SECURE in androidApp/src/main."
fi
if ! calls 'window\.decorView\.filterTouchesWhenObscured[[:space:]]*=[[:space:]]*true'; then
    fail "screen-security: tapjacking guard is not ASSIGNED true on the decor view" \
         "ADR-0010: the Android shell must set window.decorView.filterTouchesWhenObscured = true."
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
