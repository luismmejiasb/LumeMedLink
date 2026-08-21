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

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# Personal-data field names, in both languages the ecosystem uses. A parameter NAME is what the
# gate can see; the value is a runtime thing no script can judge.
PERSONAL_KEYS='rut|run|dni|nombre|name|apellido|surname|lastname|telefono|phone|celular|email|correo|direccion|address|fechanacimiento|birthdate|birthday|edad|prevision|documento'

scan() {
    grep -rniE "$1" $SRC 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' || true
}

# ── Query parameters built with a personal-data name ────────────────────────────────────────────
hits=$(scan "parameter\\(\\s*\"($PERSONAL_KEYS)\"")
if [ -n "$hits" ]; then
    fail "url-hygiene: a personal datum is being put in a query parameter" \
         "It would be copied into server, proxy and CDN logs. Send it in the body (§8.1, ADR-0017)." \
         "$hits"
fi
hits=$(scan "parameters\\.append\\(\\s*\"($PERSONAL_KEYS)\"")
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
