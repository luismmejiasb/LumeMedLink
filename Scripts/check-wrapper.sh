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
# Published at services.gradle.org/distributions/gradle-9.7.1-bin.zip.sha256, fetched and compared
# 2026-09-21. Pinned HERE as well as in the properties file on purpose: the gate used to assert only
# that the line EXISTED, so `distributionSha256Sum=` with nothing after it, or a hash of zeroes,
# passed (audit, ADR-0029). Two places that must agree is what makes editing one of them fail.
EXPECTED_DIST_SHA256="acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"
EXPECTED_DIST_HOST="services.gradle.org"
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

prop() { sed -n "s/^$1=//p" "$PROPS" | tr -d '\r' | head -1; }

# BY VALUE, not by presence.
dist_sha=$(prop distributionSha256Sum)
if [ -z "$dist_sha" ]; then
    fail "distributionSha256Sum is absent or empty" "Without it the distribution download is unverified."
elif [ "$dist_sha" != "$EXPECTED_DIST_SHA256" ]; then
    fail "distributionSha256Sum is not the published checksum for $EXPECTED_VERSION" \
         "expected $EXPECTED_DIST_SHA256, got $dist_sha. If the version moved, bump BOTH pins here and the properties file."
fi

# WHERE the distribution comes from, which nothing checked. The version assertion above only looks
# for the filename, so `https://evil.test/gradle-9.7.1-bin.zip` satisfied it: this build would then
# download and RUN a Gradle distribution from somewhere else. The checksum pin would refuse a
# tampered zip — but only while the checksum itself is right, which is the line directly above, and
# the two failures travel together in exactly the change that matters.
dist_url=$(prop distributionUrl | sed 's/\\:/:/g')
case "$dist_url" in
    https://"$EXPECTED_DIST_HOST"/*) ;;
    "") fail "distributionUrl is absent" "There is nothing pinned to verify." ;;
    http://*) fail "the distribution is downloaded over cleartext" "$dist_url" ;;
    *) fail "the distribution does not come from $EXPECTED_DIST_HOST" \
            "Got: $dist_url. The version check only ever looked at the filename, so any host with the right file name passed." ;;
esac

# Gradle's own guard against a distributionUrl that is not a Gradle distribution. It defaults to
# true; declaring it is the family rule that a default is not a decision, and it means turning it
# off is a visible edit.
[ "$(prop validateDistributionUrl)" = "true" ] ||
    fail "validateDistributionUrl is not declared true" \
         "It is the wrapper's own check on where the distribution comes from (ADR-0018)."

if [ $FAIL -eq 0 ]; then
    echo "wrapper: OK (jar matches the published checksum for $EXPECTED_VERSION)"
else
    exit 1
fi
