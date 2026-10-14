#!/bin/sh
# «Licencias» says what ships (author's decision, 2026-10-13): the list in composeResources/files/licenses.tsv is
# generated from the lockfiles and Package.resolved, and this fails when it is stale or when a shipped group has no
# licence line in config/licenses.txt. Rehearsed against bait (Scripts/rehearse-gates.sh).
#
# Usage: Scripts/check-licenses.sh   (exit non-zero on violations)
set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec python3 "$REPO_ROOT/Scripts/generate-licenses.py" --check
