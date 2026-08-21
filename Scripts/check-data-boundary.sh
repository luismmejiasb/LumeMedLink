#!/bin/sh
# The data boundary, as something a build can fail on (F14, ADR-0019).
#
# ADR-0001 is not a coding standard — it IS the product. This app exists so clinical content lives
# in LumeMed and nowhere else, and §13 says clinical content on any surface here "no es un bug, es
# otro producto". Until now that rule was marked [manual] because no lint understands semantics.
#
# A lint still does not understand semantics. What it CAN see is a NAME, and the boundary is
# crossed by named things: a field called `diagnosis`, a model with `bloodType`, a screen showing
# `allergies`. The backend confirmed the shape that makes this urgent — its `Patient` carries
# `bloodType` (encrypted) and `careDirective` in the SAME ROW as email and phone — so a DTO
# generated or hand-written from that entity brings clinical fields across unless something refuses
# them.
#
# This gate is therefore a NAME gate, and it says so. It cannot catch a clinical value in a field
# called `notes`; it catches the overwhelmingly common case, which is that the field is called what
# it is.
#
# Rehearsed against bait before being trusted.

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

SRC=$(find composeApp/src androidApp/src -mindepth 1 -maxdepth 1 -type d 2>/dev/null | tr '\n' ' ')
KT_FILES=$(find $SRC -name '*.kt' 2>/dev/null)
FAIL=0

if [ -z "$KT_FILES" ]; then
    echo "FAIL data-boundary scanned NOTHING — no .kt files under: $SRC"
    exit 1
fi

# Clinical vocabulary, Spanish and English. Deliberately SPECIFIC: `note` alone would fire on every
# code comment and get the gate disabled, so only unambiguously clinical terms are listed.
CLINICAL='diagnosis|diagnostico|diagnóstico|cie10|icd10|bloodType|grupoSanguineo|allerg|alergia|medication|medicamento|prescription|receta|labResult|examResult|resultadoExamen|vitalSign|signoVital|careDirective|directivaCuidado|clinicalNote|notaClinica|anamnesis|comorbid|posology|posologia|dosage|dosis|symptom|sintoma|treatment|tratamiento|pathology|patologia'

FLAT=$(mktemp)
trap 'rm -f "$FLAT"' EXIT
python3 - "$FLAT" $KT_FILES <<'PY'
import re, sys, pathlib
out = open(sys.argv[1], "w")
for f in sys.argv[2:]:
    src = pathlib.Path(f).read_text(errors="replace")
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    out.write(f + "\t" + re.sub(r"\s+", " ", src) + "\n")
out.close()
PY

# A clinical word appearing as an IDENTIFIER — a property, a parameter, a serial name.
hits=$(grep -oiE ".{0,50}(val|var|const val)[[:space:]]+[A-Za-z_]*($CLINICAL)[A-Za-z_]*" "$FLAT" || true)
if [ -n "$hits" ]; then
    FAIL=1
    echo ""
    echo "FAIL data-boundary: a clinical field name exists in this app"
    echo "  ADR-0001 is the product, not a style rule: clinical content here is a BOUNDARY VIOLATION,"
    echo "  the class above Critical (§13). If the backend sends it, the DTO must not decode it."
    echo "$hits" | sed 's/^/    /'
fi

hits=$(grep -oiE "@SerialName\([\"'][A-Za-z_]*($CLINICAL)[A-Za-z_]*[\"']\)" "$FLAT" || true)
if [ -n "$hits" ]; then
    FAIL=1
    echo ""
    echo "FAIL data-boundary: a clinical field is being DECODED from the contract"
    echo "  The non-clinical projection is requested in docs/backend-requests/0002 — until it exists,"
    echo "  the app decodes the fields it is allowed to hold and no others (ADR-0019)."
    echo "$hits" | sed 's/^/    /'
fi

if [ $FAIL -eq 0 ]; then
    echo "data-boundary: OK ($(echo "$KT_FILES" | wc -w | tr -d ' ') files)"
else
    exit 1
fi
