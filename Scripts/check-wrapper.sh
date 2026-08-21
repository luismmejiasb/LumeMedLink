#!/bin/sh
# Gradle wrapper integrity (F20, ADR-0018).
#
# WHY THE JAR AND NOT JUST THE PROPERTIES. `distributionSha256Sum` is what pins the Gradle
# distribution — but the code that ENFORCES that pin is gradle-wrapper.jar itself. The jar is the
# trust root the pin hangs from, it is the only binary this repo tracks, and until F20 nothing here
# ever looked at it.
#
# It was wrong. Verified against Gradle's published checksums: the committed jar was authentic
# Gradle **9.4.1**, while distributionUrl pinned **9.7.1** — inherited by copying a sibling repo's
# wrapper. Not an attack, but a swapped jar would have been exactly as invisible.
#
# Bait rehearsal is self-executing: change either the expected hash or the pinned version and this
# goes red on the next run.

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

JAR="gradle/wrapper/gradle-wrapper.jar"
PROPS="gradle/wrapper/gradle-wrapper.properties"

# Published by Gradle at services.gradle.org/distributions/gradle-9.7.1-wrapper.jar.sha256,
# fetched and compared 2026-08-21. Bump BOTH lines together, never one.
EXPECTED_VERSION="9.7.1"
EXPECTED_JAR_SHA256="7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d"
FAIL=0

fail() { FAIL=1; echo ""; echo "FAIL $1"; echo "  $2"; }

[ -f "$JAR" ] || { echo "FAIL the wrapper jar is missing: $JAR"; exit 1; }
[ -f "$PROPS" ] || { echo "FAIL $PROPS is missing"; exit 1; }

actual=$(shasum -a 256 "$JAR" 2>/dev/null | cut -d' ' -f1)
[ -n "$actual" ] || actual=$(sha256sum "$JAR" 2>/dev/null | cut -d' ' -f1)
if [ "$actual" != "$EXPECTED_JAR_SHA256" ]; then
    fail "the wrapper jar is not the expected one" \
         "expected $EXPECTED_JAR_SHA256, got $actual. Regenerate with ./gradlew wrapper --gradle-version $EXPECTED_VERSION."
fi

grep -q "gradle-${EXPECTED_VERSION}-bin.zip" "$PROPS" ||
    fail "the pinned distribution is not $EXPECTED_VERSION" \
         "The jar and the distribution must be the same version — they drifted apart once already."

grep -q '^distributionSha256Sum=' "$PROPS" ||
    fail "distributionSha256Sum is absent" "Without it the distribution download is unverified."

if [ $FAIL -eq 0 ]; then
    echo "wrapper: OK (jar matches the published checksum for $EXPECTED_VERSION)"
else
    exit 1
fi
