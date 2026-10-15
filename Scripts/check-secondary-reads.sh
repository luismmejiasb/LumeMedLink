#!/bin/sh
# Only a screen's own operation can fail it (ADR-0067).
#
# WHY: twice in one day (2026-10-15) a read that was not the screen's took the whole screen to its full-screen error —
# the bell's count, then the appointments, both on Inicio. Each time the cause was the same: the read went into the
# screen's `allOf`, which turns ANY failure into the screen's. The mechanism is a secondary read that answers the value,
# never a CareResult (core/care/SecondaryRead.kt); this gate keeps the two doors that would undo it shut:
#
#   1. Inicio has no operation of its own, so its state is `HomeContent?` — no CareLoad, no failure to hold.
#   2. Every `allOf(` joins reads that are ONE operation together. Each call site is named below with that reason;
#      a new one fails here until someone writes down why its reads are one operation.
#
# Asserted on code with comments and strings blanked by the tokenizer (ADR-0029), in src/main only.
#
# Usage: Scripts/check-secondary-reads.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

MAIN="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink"
HOME_VM="$MAIN/features/patient/home/HomeViewModel.kt"
FAIL=0

code_of() { python3 Scripts/lib/uncomment.py --lang c --strip-strings "$1"; }

# ── 1. Inicio cannot hold a failure ─────────────────────────────────────────────────────────────────
if [ ! -f "$HOME_VM" ]; then
    echo "FAIL $HOME_VM is gone: the gate has nothing to read (move it here with the file)."
    FAIL=1
else
    home=$(code_of "$HOME_VM")
    if ! printf '%s\n' "$home" | grep -Eq 'MutableStateFlow<HomeContent\?>'; then
        echo "FAIL Inicio's state is not MutableStateFlow<HomeContent?> (ADR-0067): it could hold a failure again."
        FAIL=1
    fi
    if printf '%s\n' "$home" | grep -Eq '\bCareLoad\.Failed\b|\ballOf\('; then
        echo "FAIL Inicio builds a failure or joins its reads with allOf (ADR-0067): nothing it reads is its own."
        FAIL=1
    fi
fi

# ── 2. Every allOf joins one operation, and says which ──────────────────────────────────────────────
# One line per call site: the file, then the operation its reads make up together.
ALLOWED='
features/doctor/agenda/AgendaViewModel.kt	the doctor'"'"'s day: its appointments and its pending requests (the script, ADR-0046)
'

found=$(cd "$MAIN" && find . -name '*.kt' -not -path './core/screen/ScreenLoad.kt' | sed 's|^\./||' | sort)
for file in $found; do
    count=$(code_of "$MAIN/$file" | grep -Eo '\ballOf\(' | wc -l | tr -d ' ')
    [ "$count" -eq 0 ] && continue
    if ! printf '%s\n' "$ALLOWED" | cut -f1 | grep -Fxq "$file"; then
        echo "FAIL $file joins reads with allOf, and is not named in this gate (ADR-0067)."
        echo "  Read what is not the screen's own operation with secondary(); or, if every read IS that one"
        echo "  operation, add the file to ALLOWED with the operation they make up together."
        FAIL=1
    fi
done

# The list cannot keep a file that no longer joins anything: a stale entry is an exemption waiting for a regression.
printf '%s\n' "$ALLOWED" | cut -f1 | while read -r file; do
    [ -z "$file" ] && continue
    if [ ! -f "$MAIN/$file" ] || ! code_of "$MAIN/$file" | grep -Eq '\ballOf\('; then
        echo "FAIL $file is named in ALLOWED but joins nothing with allOf: remove it."
        exit 1
    fi
done || FAIL=1

if [ "$FAIL" -ne 0 ]; then
    exit 1
fi
echo "ok: only a screen's own operation can fail it (ADR-0067)"
