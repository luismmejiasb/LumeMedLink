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
# WIDENED 2026-09-21 (audit, ADR-0029). It used to match only after `val`, `var` or `const val`, so
# it saw a property and nothing else. Four baits walked past it, all of them ordinary Kotlin:
#     fun render(motivoClinico: String, diagnosis: String)   <- function parameters
#     enum class Reason { DIAGNOSIS_FOLLOW_UP }               <- an enum entry
#     class AllergyBanner                                     <- a type name
#     typealias Prescription = String                         <- an alias
# Enumerating syntactic positions is the same losing game as naming bad spellings (F20, ADR-0024):
# it only ever catches the ones someone thought of. So the rule is now the whole-code one — a
# clinical word must not appear as an identifier ANYWHERE, in any position, in any file. Verified
# green against today's tree before being adopted, which is what makes a rule that strict safe.
#
# And `motivo` was missing from the vocabulary, which is the field §1.0 names by hand:
# "Citas: existencia, fecha, hora, lugar/modalidad, con quién. **Nunca el motivo clínico.**"
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
CLINICAL='diagnosis|diagnostico|diagnóstico|cie10|icd10|bloodType|grupoSanguineo|allerg|alergia|medication|medicamento|prescription|receta|labResult|examResult|resultadoExamen|vitalSign|signoVital|careDirective|directivaCuidado|clinicalNote|notaClinica|anamnesis|comorbid|posology|posologia|dosage|dosis|symptom|sintoma|treatment|tratamiento|pathology|patologia|motivo|chiefComplaint|motivoConsulta|reasonForVisit|medicalRecord|historiaClinica'

# Comments are blanked by the shared tokenizer (ADR-0029) so a KDoc quoting ADR-0001 cannot trip
# the gate it describes. String literals are KEPT on purpose: a clinical word in a string is a
# serial name, a query key or a label, and every one of those crosses the boundary too.
SCAN=$(mktemp)
trap 'rm -f "$SCAN"' EXIT
for f in $KT_FILES; do
    python3 Scripts/lib/uncomment.py --lang c "$f" 2>/dev/null |
        grep -niE "[A-Za-z_]*($CLINICAL)[A-Za-z_]*" | sed "s|^|$f:|" >> "$SCAN"
done

if [ -s "$SCAN" ]; then
    FAIL=1
    echo ""
    echo "FAIL data-boundary: a clinical name exists in this app"
    echo "  ADR-0001 is the product, not a style rule: clinical content here is a BOUNDARY VIOLATION,"
    echo "  the class above Critical (§13). If the backend sends it, the DTO must not decode it."
    echo "  Any position counts — property, parameter, enum entry, type name, alias, serial name."
    sed 's/^/    /' "$SCAN"
fi

if [ $FAIL -eq 0 ]; then
    echo "data-boundary: OK ($(echo "$KT_FILES" | wc -w | tr -d ' ') files)"
else
    exit 1
fi
