#!/bin/sh
# Backup and transfer posture gate (F6, ADR-0015).
#
# The thing this gate exists to prevent is subtle and was MEASURED, not assumed:
# `android:allowBackup="false"` does NOT stop device-to-device transfer on targetSdk >= 31 (compat
# change IGNORE_ALLOW_BACKUP_IN_D2D). On a live API 37 emulator the D2D transport extracted 3072
# bytes from this package while the cloud transport refused it in the same session. So the two
# attributes are complementary, and a reviewer who "cleans up the redundant one" reopens a hole.
#
# WORSE THAN NOTHING: a data_extraction_rules.xml that omits <device-transfer> makes the framework
# fall back to an EMPTY (transfer-everything) scheme for D2D. Adding the file without that section
# is a downgrade, not a partial improvement — which is why its absence is a hard failure here and
# not a warning.
#
# Rehearsed against bait before being trusted.
#
# Usage: Scripts/check-backup-posture.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

MANIFEST="androidApp/src/main/AndroidManifest.xml"
RULES="androidApp/src/main/res/xml/data_extraction_rules.xml"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# Attribute lines only — a mention inside an XML comment is prose, not configuration.
manifest_attr() {
    grep -E "$1" "$MANIFEST" 2>/dev/null | grep -v '<!--' | grep -vE '^\s+[a-z].*-->' | grep -q 'android:'
}

[ -f "$MANIFEST" ] || { echo "FAIL manifest missing: $MANIFEST"; exit 1; }

# ── The two attributes, both required ───────────────────────────────────────────────────────────
if ! manifest_attr 'android:allowBackup="false"'; then
    fail "backup: allowBackup is not false" \
         "Cloud backup would re-materialize this app's data on another device (§8.5)."
fi
if grep -qE 'android:allowBackup="true"' "$MANIFEST"; then
    fail "backup: allowBackup is explicitly true" "That is the opposite of ADR-0015."
fi
if ! manifest_attr 'android:dataExtractionRules='; then
    fail "backup: dataExtractionRules is not declared" \
         "allowBackup=false does NOT cover device-to-device transfer at targetSdk>=31; this attribute is what does (ADR-0015)."
fi

# ── The rules file, and the section whose absence is a downgrade ────────────────────────────────
if [ ! -f "$RULES" ]; then
    fail "backup: the rules resource is missing ($RULES)" \
         "The manifest points at a resource that does not exist; the build would break and the D2D path would be open."
else
    grep -q '<cloud-backup>' "$RULES" || fail "backup: <cloud-backup> section missing" \
        "Every domain must be excluded from cloud backup explicitly (a default is not a decision)."

    if ! grep -q '<device-transfer>' "$RULES"; then
        fail "backup: <device-transfer> section missing — THIS IS WORSE THAN HAVING NO RULES FILE" \
             "Without it the framework uses an empty, transfer-everything scheme for D2D (ADR-0015)."
    fi

    # cross-platform-transfer is OPT-IN: writing the section is what ENABLES Android<->iOS app-data
    # export. There is no way to use it to disable anything, so its presence is always a widening.
    # (An earlier investigation recommended adding it "for symmetry" — that would have opened a new
    # path, which is why this line exists.)
    if grep -q 'cross-platform-transfer' "$RULES"; then
        fail "backup: <cross-platform-transfer> is present" \
             "That section ENABLES export to another platform; it is opt-in and this app never wants it (ADR-0015)."
    fi

    if grep -qE '<include' "$RULES"; then
        fail "backup: an <include> re-admits data into a backup or transfer" \
             "This app excludes everything; an include is how a leak comes back." \
             "$(grep -nE '<include' "$RULES")"
    fi

    # THE NINE DOMAINS BY NAME, never by counting tags. Counting was this gate's own false green,
    # caught by an adversarial bait: renaming device_file -> device_files kept nine <exclude> lines
    # and the gate stayed OK — while the framework SILENTLY SKIPS an unrecognised domain (no build
    # error, only a VERBOSE log line), so that directory would still travel. A typo is exactly the
    # mistake a human makes here, so the check has to be able to see it.
    #
    # And all nine are required: the framework walks nine SEPARATE trees, so excluding "root" does
    # not cover file/database/sharedpref.
    for section in cloud-backup device-transfer; do
        body=$(sed -n "/<$section>/,/<\/$section>/p" "$RULES")
        for domain in root file database sharedpref external device_root device_file device_database device_sharedpref; do
            echo "$body" | grep -q "<exclude domain=\"$domain\"" || {
                fail "backup: <$section> does not exclude domain \"$domain\"" \
                     "That directory still travels. An unrecognised or missing domain fails silently in the framework (ADR-0015)."
            }
        done
        unknown=$(echo "$body" | grep -oE 'domain="[a-z_]+"' | sed 's/domain="//;s/"//' \
            | grep -vxE 'root|file|database|sharedpref|external|device_root|device_file|device_database|device_sharedpref' || true)
        if [ -n "$unknown" ]; then
            fail "backup: <$section> names a domain the framework does not know: $unknown" \
                 "It is silently ignored at runtime, so the rule does nothing (ADR-0015)."
        fi
    done
fi

if [ $FAIL -eq 0 ]; then
    echo "backup-posture: OK"
else
    exit 1
fi
