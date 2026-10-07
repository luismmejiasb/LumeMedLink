#!/bin/sh
# Exports the design kit's LAST COMMIT to a directory this repo can build against (ADR-0033, task 0013).
#
# Why: the kit is consumed by path, as a composite build, so Gradle compiles its WORKING TREE. When
# another session is halfway through an edit in ../LumeUIComposer, this repo's build breaks on code
# that is not this repo's and was never committed (measured 2026-10-07). A verification run must
# measure THIS repo against a kit that exists — its last commit — not against somebody's keystrokes.
#
# Read-only towards the kit: `git archive` touches neither its working tree nor its index, and adds
# no worktree metadata to its .git. The export's directory is named LumeUIComposer because an
# included build's name is its directory's (androidApp looks it up by that name).
#
# Usage: Scripts/kit-snapshot.sh <parent-dir>
#   then: ./gradlew -Plume.kit.path=<parent-dir>/LumeUIComposer …
# Prints the path to pass.

set -eu
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
KIT="$REPO_ROOT/../LumeUIComposer"
PARENT=${1:?usage: Scripts/kit-snapshot.sh <parent-dir>}
DEST="$PARENT/LumeUIComposer"
[ -d "$KIT/.git" ] || { echo "kit-snapshot: no git checkout at $KIT" >&2; exit 1; }
rm -rf "$DEST"
mkdir -p "$DEST"
git -C "$KIT" archive HEAD | tar -x -C "$DEST"
echo "kit-snapshot: $(git -C "$KIT" rev-parse --short HEAD) -> $DEST" >&2
echo "$DEST"
