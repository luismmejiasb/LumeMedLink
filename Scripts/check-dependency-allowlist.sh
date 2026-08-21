#!/bin/sh
# Dependency allowlist gate (§8.8) — reads what actually RESOLVED (the lockfiles), not what was
# declared: a transitive rides in through the same door as a direct dependency.
#
# Two layers, denylist first:
#   D  The named denylist (§8.1): crash/analytics SDKs and network-owning image loaders are
#      refused BY NAME with the reason, even if an allowlist prefix would admit them.
#   A  Everything in a lockfile must prefix-match a line of config/dependency-allowlist.txt.
#      A new group failing here is the gate WORKING: add the line consciously, with its ADR (§13).
#
# Usage: Scripts/check-dependency-allowlist.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

ALLOWLIST="config/dependency-allowlist.txt"
# TWO find calls on purpose: `-maxdepth` is a GLOBAL option in BSD find, and combining two of them
# with `-o` silently reduced the search to depth 1 — the module lockfiles were never read and a
# bait line sailed through green. Caught by this script's own bait rehearsal (bitácora 0003).
LOCKFILES=$( { find . -maxdepth 2 -name 'gradle.lockfile'; find . -maxdepth 1 -name 'settings-gradle.lockfile'; } | sort)
FAIL=0

if [ ! -f "$ALLOWLIST" ]; then
    echo "FAIL allowlist file missing: $ALLOWLIST"
    exit 1
fi
if [ -z "$LOCKFILES" ]; then
    echo "FAIL no lockfiles found — dependency locking is off, which is itself a violation (§0)."
    exit 1
fi

# group:artifact pairs actually resolved, deduplicated
MODULES=$(cat $LOCKFILES | grep -v '^#' | grep -v '^empty' | cut -d: -f1,2 | sort -u)

# ── D · denylist, with reasons (checked first: it wins over any allowlist prefix) ───────────────
deny() {
    prefix=$1; reason=$2
    hits=$(echo "$MODULES" | grep -E "^${prefix}" || true)
    if [ -n "$hits" ]; then
        FAIL=1
        echo ""
        echo "FAIL denylisted dependency: $hits"
        echo "  $reason"
    fi
}
deny 'com\.google\.firebase'        'Default-deny of crash/analytics SDKs (§8.1). Firebase is "one line away" with this IdP; the line stays unwritten.'
deny 'com\.google\.android\.gms'    'Play Services drags analytics surface (§8.1). Play Integrity, when it lands, gets its own reviewed coordinate.'
deny 'io\.sentry'                   'Default-deny of crash/analytics SDKs (§8.1).'
deny 'com\.bugsnag'                 'Default-deny of crash/analytics SDKs (§8.1).'
deny 'org\.acra'                    'Default-deny of crash/analytics SDKs (§8.1).'
deny 'io\.coil-kt'                  'Image loader with its own network path (§7): remote bytes go through the stack.'
deny 'com\.github\.bumptech\.glide' 'Image loader with its own network path (§7).'
deny 'com\.squareup\.picasso'       'Image loader with its own network path (§7).'

# ── A · everything resolved must be allowlisted ─────────────────────────────────────────────────
PREFIXES=$(grep -v '^#' "$ALLOWLIST" | grep -v '^[[:space:]]*$')
for module in $MODULES; do
    group=$(echo "$module" | cut -d: -f1)
    allowed=0
    for p in $PREFIXES; do
        case "$group" in "$p"*) allowed=1; break ;; esac
    done
    if [ $allowed -eq 0 ]; then
        FAIL=1
        echo ""
        echo "FAIL group not in allowlist: $module"
        echo "  Add it to $ALLOWLIST consciously — a new dependency needs its ADR (§13)."
    fi
done

if [ $FAIL -eq 0 ]; then
    echo "dependency-allowlist: OK ($(echo "$MODULES" | wc -l | tr -d ' ') modules checked)"
else
    exit 1
fi
