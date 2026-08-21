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
# No depth limit: the constitution's own tree (§4) grows nested Gradle modules, and a lockfile the
# gate cannot see is a dependency set nobody checks. `build` is pruned so stale copies under build
# outputs never masquerade as source of truth.
LOCKFILES=$(find . -path ./build -prune -o -path '*/build' -prune -o \
    \( -name 'gradle.lockfile' -o -name 'settings-gradle.lockfile' -o -name 'buildscript-gradle.lockfile' \) -print | sort)
FAIL=0

if [ ! -f "$ALLOWLIST" ]; then
    echo "FAIL allowlist file missing: $ALLOWLIST"
    exit 1
fi
if [ -z "$LOCKFILES" ]; then
    echo "FAIL no lockfiles found — dependency locking is off, which is itself a violation (§0)."
    exit 1
fi

# The EXPECTED set must all be present. Without this, deleting composeApp/gradle.lockfile — the
# module that holds the entire product dependency set — left the gate green: it only failed when
# ZERO lockfiles existed. Verified.
for expected in ./gradle.lockfile ./settings-gradle.lockfile ./buildscript-gradle.lockfile; do
    [ -f "$expected" ] || {
        echo "FAIL expected lockfile missing: $expected"
        echo "  A dependency set nobody locked is a dependency set nobody checks (§0, ADR-0018)."
        exit 1
    }
done
for module in $(grep -oE '^include\("[^"]+"\)' settings.gradle.kts | sed 's/include("://;s/")//'); do
    [ -f "./$module/gradle.lockfile" ] || {
        echo "FAIL module :$module is declared in settings.gradle.kts but has no gradle.lockfile"
        echo "  Run ./gradlew build --write-locks (ADR-0018)."
        exit 1
    }
done

# group:artifact pairs actually resolved, deduplicated
MODULES=$(cat $LOCKFILES | grep -v '^#' | grep -v '^empty' | cut -d: -f1,2 | sort -u)

# The SHIPPED subset: modules that reach a configuration which ends up on the device. A lockfile
# line is `group:artifact:version=config1,config2,…`, and until F20 this script discarded the
# configuration field entirely — which is why the allowlist could carry a heading saying
# "lint-time only, never shipped" over a group that does ship (org.slf4j, dragged in by
# kotlinx-coroutines-slf4j). A claim no gate can check is a claim that drifts.
#
# Excluded from "shipped": test, lint, tooling, annotation-processor and metadata configurations.
SHIPPED_MODULES=$(cat $LOCKFILES | grep -v '^#' | grep -v '^empty' | awk -F= '
    {
        split($2, cfgs, ",")
        for (i in cfgs) {
            c = cfgs[i]
            if (c ~ /(Runtime|Compile)Classpath$/ &&
                c !~ /[Tt]est/ && c !~ /Lint/ && c !~ /Tool/ &&
                c !~ /AnnotationProcessor/ && c !~ /Metadata/) {
                print $1; break
            }
        }
    }' | cut -d: -f1,2 | sort -u)

# ── D · denylist, with reasons (checked first: it wins over any allowlist prefix) ───────────────
# A denylisted group FAILS when it reaches the device. When it only appears on a build-time
# configuration (AGP's own lint toolchain drags an HTTP client, for instance) it is reported as a
# visible note instead: it is a real supply-chain surface on the developer's machine, but blocking
# the build over AGP's internals would get this gate switched off, and a gate that gets switched
# off protects nothing.
deny() {
    prefix=$1; reason=$2
    shipped=$(echo "$SHIPPED_MODULES" | grep -E "^${prefix}" || true)
    if [ -n "$shipped" ]; then
        FAIL=1
        echo ""
        echo "FAIL denylisted dependency SHIPS IN THE APP: $shipped"
        echo "  $reason"
        return
    fi
    buildtime=$(echo "$MODULES" | grep -E "^${prefix}" || true)
    if [ -n "$buildtime" ]; then
        echo "note: denylisted group present BUILD-TIME only (not shipped): $(echo "$buildtime" | tr '\n' ' ')"
    fi
}
deny 'com\.google\.firebase'        'Default-deny of crash/analytics SDKs (§8.1). Firebase is "one line away" with this IdP; the line stays unwritten.'
deny 'com\.google\.android\.gms'    'Play Services drags analytics surface (§8.1). Play Integrity, when it lands, gets its own reviewed coordinate.'
deny 'io\.sentry'                   'Default-deny of crash/analytics SDKs (§8.1).'
deny 'com\.bugsnag'                 'Default-deny of crash/analytics SDKs (§8.1).'
deny 'org\.acra'                    'Default-deny of crash/analytics SDKs (§8.1).'
deny 'androidx\.health'             'CLINICAL DATA. ADR-0001 is the product: this app never holds it. Named here so the refusal explains itself instead of being an absence — the prefix allowlist used to admit androidx.health.connect silently.'
deny 'androidx\.security'           'EncryptedSharedPreferences is deprecated and is NOT the Android secret store of this repo (ADR-0009).'
deny 'com\.appsflyer'               'Attribution/tracking SDK (§8.1).'
deny 'com\.amplitude'               'Analytics SDK (§8.1).'
deny 'com\.mixpanel'                'Analytics SDK (§8.1).'
deny 'com\.segment'                 'Analytics/CDP SDK (§8.1).'
deny 'com\.datadoghq'               'RUM/monitoring SDK that ships user data off-device (§8.1).'
deny 'com\.newrelic'                'RUM/monitoring SDK (§8.1).'
deny 'io\.embrace'                  'Mobile observability SDK (§8.1).'
deny 'org\.apache\.httpcomponents'  'A SECOND HTTP client. §7: cero salida de red fuera del stack (ADR-0004).'
deny 'io\.coil-kt'                  'Image loader with its own network path (§7): remote bytes go through the stack.'
deny 'com\.github\.bumptech\.glide' 'Image loader with its own network path (§7).'
deny 'com\.squareup\.picasso'       'Image loader with its own network path (§7).'

# ── A · everything resolved must be allowlisted ─────────────────────────────────────────────────
# Read LINE BY LINE with comments stripped per line. `grep -v '^#'` only drops comments anchored
# at column 0, and the loop below used to word-split — so an indented note like
# `    # transitive io.evil.beacon pulled in by ops` injected `io.evil.beacon` as an allowlist
# entry. Verified: that exact shape admitted the group green. The file's header is prose full of
# dotted group names, so the exploit shape is the documented practice.
ALLOWED=$(sed 's/#.*//' "$ALLOWLIST" | tr -d '[:blank:]' | grep -v '^$')
for module in $MODULES; do
    group=$(echo "$module" | cut -d: -f1)
    allowed=0
    for p in $ALLOWED; do
        # EXACT match. Prefix matching had no namespace boundary, so any group merely BEGINNING
        # with an allowed string was admitted: `androidx.health.connect` (a clinical-data API),
        # `io.ktorexploit`, `org.jetbrainsevil` all passed green. Measured, not argued.
        if [ "$group" = "$p" ]; then allowed=1; break; fi
    done
    if [ $allowed -eq 0 ]; then
        FAIL=1
        echo ""
        echo "FAIL group not in allowlist: $module"
        echo "  Add it to $ALLOWLIST consciously — a new dependency needs its ADR (§13)."
    fi
done

# ── C · CI actions are code too ─────────────────────────────────────────────────────────────────
# A third-party GitHub Action runs in this repo's CI with its token and its build outputs, and a
# TAG is mutable: whoever controls the action's repository can repoint v4 at new code. Pinning by
# commit SHA is the same discipline as pinning a dependency version — and the same reasoning that
# put distributionSha256Sum on the Gradle wrapper.
WORKFLOWS=$(find .github/workflows -name '*.yml' 2>/dev/null || true)
if [ -n "$WORKFLOWS" ]; then
    hits=$(grep -nE '^\s*-?\s*uses:\s*[^@]+@(v?[0-9][^ ]*|main|master)\s*$' $WORKFLOWS || true)
    if [ -n "$hits" ]; then
        FAIL=1
        echo ""
        echo "FAIL a CI action is pinned by a mutable tag or branch"
        echo "  Pin it by commit SHA (keep the tag as a trailing comment). ADR-0018."
        echo "$hits" | sed 's/^/    /'
    fi
fi

if [ $FAIL -eq 0 ]; then
    echo "dependency-allowlist: OK ($(echo "$MODULES" | wc -l | tr -d ' ') modules checked)"
else
    exit 1
fi
