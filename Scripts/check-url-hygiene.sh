#!/bin/sh
# URL hygiene gate (F13, ADR-0017) — no personal datum may be placed in a URL.
#
# WHY A URL IS NOT LIKE A BODY. A request body is seen by the server and nothing else. A URL is
# copied, by default and without anyone deciding it, into:
#   · the server's own access log, query string included;
#   · every proxy, load balancer and CDN in between, and their logs;
#   · this app's own log line, unless the redactor happens to cover that segment;
#   · exception messages, which travel to crash reporters (this repo has already had that leak).
# So a RUT in a query string is a RUT written to half a dozen places nobody audited, whereas the
# same RUT in a body is written to one. The backend already made this call for its RUT search — it
# takes the RUT in the BODY, never the URL — and this gate keeps the app on the same side.
#
# Identifiers in the PATH are a different case and are allowed: they are opaque server-side ids,
# not personal data on their own. The log redactor replaces them anyway (NetworkLogSink).
#
# Rehearsed against bait before being trusted.
#
# Usage: Scripts/check-url-hygiene.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

# PRODUCTION source sets only. Tests are excluded ON PURPOSE and it is worth saying why: a test
# that proves the redactor removes a RUT from a URL has to build a URL with a RUT in it. Scanning
# them would make the gate red for the code that demonstrates the defense working — and a gate
# that punishes its own evidence gets disabled by the next person in a hurry.
SRC=$(find composeApp/src androidApp/src -mindepth 1 -maxdepth 1 -type d 2>/dev/null | grep -vE '([Tt]est)$' | tr '\n' ' ')
FAIL=0

KT_FILES=$(find $SRC -name '*.kt' 2>/dev/null)
if [ -z "$KT_FILES" ]; then
    echo "FAIL url-hygiene scanned NOTHING — no .kt files under: $SRC"
    echo "  A gate that reports OK while reading no files is worse than no gate (§9)."
    exit 1
fi

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# Personal-data field names, in both languages the ecosystem uses. A parameter NAME is what the
# gate can see; the value is a runtime thing no script can judge.
# Capitalised forms, for the camelCase middle of a name (`patientRut`). Kept separate from the
# lowercase list on purpose: matching a bare lowercase key anywhere would redden `filename`,
# and a gate with false positives is a gate someone disables.
PERSONAL_KEYS_CAPS='Rut|Run|Dni|Nombre|Name|Apellido|Surname|Lastname|Telefono|Phone|Celular|Email|Correo|Direccion|Address|Birthdate|Birthday|Edad|Prevision|Documento'
PERSONAL_KEYS='rut|run|dni|nombre|name|apellido|surname|lastname|telefono|phone|celular|email|correo|direccion|address|fechanacimiento|birthdate|birthday|edad|prevision|documento'

# THREE holes this function used to have, all measured (bitácora 0020):
#  · It anchored the key to the WHOLE parameter name, so `parameter("patientRut", …)` — the
#    camelCase shape anyone would actually type — passed green while `parameter("rut", …)` failed.
#  · It was line-based, so the same call wrapped across lines by ktlint (this repo's limit is 120,
#    and ktlintCheck runs in CI) was invisible.
#  · It printed OK and exited 0 when it scanned NOTHING. A source-set rename would have turned the
#    gate into a green no-op — the repo's own recorded failure class.
#
# So: two passes. The line pass keeps line numbers for a readable report; the collapsed pass
# catches the wrapped form. Both run over the same files, and the file list is verified non-empty
# before either.
# ONE pass over all sources, built once. The first version spawned a python process per file per
# rule — correct but ~160 process starts, and it timed out mid-rehearsal. Now the whole tree is
# stripped of comments and flattened into a single `path<TAB>content` line per file, so every rule
# is one grep over one file, and a wrapped call still matches because the newlines are gone.
#
# Comments are stripped BEFORE flattening and that is not tidiness: this repo's own KDoc quotes a
# leaky URL as the example of what not to do, and the flattened pass read the explanation as the
# offence.
FLAT=$(mktemp)
trap 'rm -f "$FLAT"' EXIT
python3 - "$FLAT" $KT_FILES <<'PY'
import re, sys, pathlib
out = open(sys.argv[1], "w")
for f in sys.argv[2:]:
    src = pathlib.Path(f).read_text(errors="replace")
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)   # block comments and KDoc
    src = re.sub(r"//[^\n]*", " ", src)                 # line comments
    out.write(f + "\t" + re.sub(r"\s+", " ", src) + "\n")
out.close()
PY

# Two sensitivities on purpose. The whole-name rules are case-INSENSITIVE (`rut`, `RUT`, `Rut` are
# all the same field). The camelCase rule must be case-SENSITIVE, because its whole discrimination
# is the capital: `patientRut` is a personal datum and `filename` is not, and they differ only in
# whether the key starts a word. Running that rule with -i reddened `filename` — a false positive,
# and a gate with those gets switched off.
scan() {
    grep -oiE ".{0,60}$1" "$FLAT" 2>/dev/null || true
}

scan_cs() {
    grep -oE ".{0,60}$1" "$FLAT" 2>/dev/null || true
}

# ── Query parameters built with a personal-data name ────────────────────────────────────────────
hits=$(scan "parameter\\(\\s*\"($PERSONAL_KEYS)[A-Za-z_]*\"";
       scan_cs "parameter\\(\\s*\"[a-z][A-Za-z_]*($PERSONAL_KEYS_CAPS)[A-Za-z_]*\"")
if [ -n "$hits" ]; then
    fail "url-hygiene: a personal datum is being put in a query parameter" \
         "It would be copied into server, proxy and CDN logs. Send it in the body (§8.1, ADR-0017)." \
         "$hits"
fi
hits=$(scan "parameters\\.append\\(\\s*\"($PERSONAL_KEYS)[A-Za-z_]*\"";
       scan_cs "parameters\\.append\\(\\s*\"[a-z][A-Za-z_]*($PERSONAL_KEYS_CAPS)[A-Za-z_]*\"")
if [ -n "$hits" ]; then
    fail "url-hygiene: a personal datum is being appended to the query" \
         "Same leak, different API (ADR-0017)." "$hits"
fi

# ── A literal URL or path string carrying one ───────────────────────────────────────────────────
hits=$(scan "[\"']([^\"']*[?&])($PERSONAL_KEYS)=")
if [ -n "$hits" ]; then
    fail "url-hygiene: a literal query string names a personal-data field" \
         "Even in a constant, it is the shape that ends up in logs (ADR-0017)." "$hits"
fi

# ── Building a URL by string concatenation, which defeats every check above ─────────────────────
hits=$(scan 'encodedQuery\s*=|url\.parameters\.appendAll|buildString.*\?.*=\$')
if [ -n "$hits" ]; then
    fail "url-hygiene: a query string is being assembled by hand" \
         "Hand-built queries escape both this gate and the stack's encoding. Use typed parameters (ADR-0017)." \
         "$hits"
fi

if [ $FAIL -eq 0 ]; then
    echo "url-hygiene: OK"
else
    exit 1
fi
