#!/bin/sh
# Clipboard and keyboard gate (F3, ADR-0013) — the two places a personal datum leaves the app
# without any network call.
#
# The clipboard is shared with EVERY app on the phone. Copying a RUT or a phone number hands it to
# whatever else is installed, and on Android 13+ the system additionally shows the copied content
# in an overlay. Android offers no API to keep a clip off the cross-device sync or to expire it, so
# the mitigation there is to not offer copying at all (§8.9) — which is what this gate enforces.
#
# The keyboard sees every keystroke. A third-party IME can learn a value and suggest it later in
# another app; iOS can refuse third-party keyboards app-wide, Android CANNOT (§8.10, declared, not
# faked). What both can do is refuse autocorrect and capitalization suggestions on sensitive
# fields — which is why every such field must go through core/input/SensitiveTextField.
#
# Rehearsed against bait before being trusted.
#
# Usage: Scripts/check-input-surfaces.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

SRC="composeApp/src androidApp/src"
PRIMITIVE="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/input/SensitiveTextField.kt"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# Comment lines never count: a KDoc naming the forbidden API is documentation, not a call.
scan() {
    grep -rnE "$1" $SRC 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' || true
}

# ── The clipboard: no legitimate caller exists yet ──────────────────────────────────────────────
hits=$(scan 'ClipboardManager|LocalClipboardManager|LocalClipboard|ClipData|setPrimaryClip|ClipDescription|UIPasteboard')
if [ -n "$hits" ]; then
    fail "input: clipboard API in use" \
         "A copied RUT or phone is readable by every app on the device (§8.9). No feature needs copy yet; when one does it arrives with its own reviewed seam." \
         "$hits"
fi

# SelectionContainer is the quiet form of the same leak: it hands arbitrary rendered text to the
# system's copy toolbar, which puts it on the same shared clipboard.
hits=$(scan 'SelectionContainer')
if [ -n "$hits" ]; then
    fail "input: selectable text container in use" \
         "Selectable text gives the copy toolbar — and therefore the shared clipboard — the rendered value (§8.9)." \
         "$hits"
fi

# ── Text input must go through the hardened primitive ───────────────────────────────────────────
hits=$(grep -rnE '\b(BasicTextField|OutlinedTextField|TextField)\s*\(' $SRC 2>/dev/null \
    | grep -v "^$PRIMITIVE:" \
    | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' || true)
if [ -n "$hits" ]; then
    fail "input: raw text field outside the hardened primitive" \
         "Sensitive input goes through core/input/SensitiveTextField (ADR-0013), where the keyboard attributes are decided once." \
         "$hits"
fi

# ── Weakenings of the primitive itself ──────────────────────────────────────────────────────────
hits=$(scan 'autoCorrectEnabled[[:space:]]*=[[:space:]]*true|autoCorrect[[:space:]]*=[[:space:]]*true')
if [ -n "$hits" ]; then
    fail "input: autocorrect re-enabled on an input field" \
         "Autocorrect is how a typed personal datum enters the keyboard's learned vocabulary (ADR-0013)." \
         "$hits"
fi
hits=$(scan 'KeyboardCapitalization\.(Words|Sentences|Characters)')
if [ -n "$hits" ]; then
    fail "input: capitalization suggestions enabled" \
         "Same path as autocorrect: the value resurfaces as a suggestion in another app (ADR-0013)." \
         "$hits"
fi

# ── The OS structure-export channels (F3 reopened, ADR-0024) ────────────────────────────────────
# A different mechanism from the keyboard and the clipboard, and from FLAG_SECURE: autofill and
# content capture hand another process a STRUCTURED copy of the screen, text included. Compose opts
# into both on the app's behalf — every text field publishes ContentDataType.Text semantics with no
# condition and no opt-out — so this is a present-and-absent pair, not a style rule.

# PRESENCE, and on the RIGHT view. `AndroidComposeView` overrides getImportantForAutofill() to a
# hardcoded YES, so the exclusion only works from an ancestor: applied anywhere but the decor view
# it is silently discarded, which is precisely a change that would look correct in review.
present_in_shell() {
    grep -rh --include='*.kt' "$1" androidApp/src 2>/dev/null | grep -vqE '^[[:space:]]*(//|\*|/\*)'
}
if ! present_in_shell 'window\.decorView\.denyAutofillExport()'; then
    fail "input: autofill structure export not excluded at the window root" \
         "The Android shell must call window.decorView.denyAutofillExport() (ADR-0024). On any other view the assignment is discarded without error."
fi
if ! present_in_shell 'denyContentCapture()'; then
    fail "input: content capture not disabled by the app" \
         "The shell must call denyContentCapture() (ADR-0024) so the channel survives a screen that loses FLAG_SECURE."
fi

# ABSENCE, written as an ALLOWLIST rather than a denylist, because the first draft of this gate was
# a denylist and its own bait walked straight through it: the pattern accepted the qualifier `View.`
# and the bait wrote `android.view.View.IMPORTANT_FOR_AUTOFILL_YES`. Same shape as the `androidx.`
# prefix hole in F20 — naming the bad values can only ever catch the spellings someone thought of.
#
# So: EVERY assignment to importantForAutofill must be NO_EXCLUDE_DESCENDANTS, and every
# setContentCaptureEnabled call must pass false. Anything else, however spelled or qualified, and
# including values that do not exist yet, is a failure. Assignment-shaped so the instrumented test
# may still NAME the YES constant to reproduce Compose's override.
hits=$(scan 'importantForAutofill[[:space:]]*=' | grep -vE 'IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS' || true)
if [ -n "$hits" ]; then
    fail "input: autofill importance set to something other than NO_EXCLUDE_DESCENDANTS" \
         "The only permitted value is IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS (ADR-0024). Anything else puts this app's fields back into the structure handed to the user's autofill service." "$hits"
fi
hits=$(scan 'setContentCaptureEnabled[[:space:]]*\(' | grep -vE 'setContentCaptureEnabled[[:space:]]*\([[:space:]]*false[[:space:]]*\)' || true)
if [ -n "$hits" ]; then
    fail "input: content capture enabled, or enabled conditionally" \
         "The only permitted call is setContentCaptureEnabled(false) (ADR-0024) — a variable argument is a decision made somewhere this gate cannot read." "$hits"
fi
hits=$(scan 'IMPORTANT_FOR_CONTENT_CAPTURE_YES')
if [ -n "$hits" ]; then
    fail "input: content capture opt-in in app code" \
         "Compose already opts in on our behalf; app code must never add another (ADR-0024)." "$hits"
fi

if [ $FAIL -eq 0 ]; then
    echo "input-surfaces: OK"
else
    exit 1
fi
