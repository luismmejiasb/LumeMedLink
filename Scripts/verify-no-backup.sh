#!/bin/sh
# Repeatable device proof of the backup/transfer posture (F6, ADR-0015).
#
# THE TRAP THIS SCRIPT IS BUILT AROUND: `bmgr backupnow` answering "Backup is not allowed" does NOT
# mean the manifest opted out. ERROR_BACKUP_NOT_ALLOWED covers several causes — backup globally
# disabled, the package in stopped state, and the manifest — so a bare negative is worthless. This
# session hit both confounds live: once with the Backup Manager disabled (the CONTROL failed too),
# once with the package freshly installed and stopped (which made a D2D leak look like a refusal).
#
# So every assertion here is paired with a positive control in the same session and transport, and
# the script refuses to conclude anything if the control does not behave.
#
# Usage: Scripts/verify-no-backup.sh   (needs a booted emulator/device and the app installed)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG="com.luismejias.lumemedlink"
# A system package that DOES allow backup. It is the control: if it cannot be backed up, the
# measurement apparatus is broken and our package's refusal proves nothing.
CONTROL_PKG="com.android.providers.settings"
D2D="com.google.android.gms/.backup.migrate.service.D2dTransport"
CLOUD="com.android.localtransport/.LocalTransport"
FAIL=0

fail() { FAIL=1; echo ""; echo "FAIL $1"; echo "  $2"; }

LIVE_CONTROL=0
[ "${1:-}" = "--with-live-control" ] && LIVE_CONTROL=1

d2d_backup() { "$ADB" shell bmgr backupnow "$1" 2>&1; }

[ -x "$ADB" ] || { echo "FAIL adb not found at $ADB"; exit 1; }
[ -n "$("$ADB" devices | sed -n '2p')" ] || { echo "FAIL no device attached"; exit 1; }

# ── Preconditions, because each one silently invalidates the result ─────────────────────────────
echo "0/4 · preconditions…"
"$ADB" shell pm list packages 2>/dev/null | grep -q "package:$PKG" || {
    echo "FAIL $PKG is not installed. Install it first (./gradlew :androidApp:installDebug)."
    exit 1
}
# A stopped package is refused for a reason that has nothing to do with the manifest — the exact
# confound that made a real D2D leak look like a refusal earlier in this repo's history.
"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3
if "$ADB" shell dumpsys package "$PKG" 2>/dev/null | grep -q 'stopped=true'; then
    fail "the package is in stopped state" "Any refusal now would be meaningless. Launch it and retry."
    exit 1
fi
"$ADB" shell bmgr enable true >/dev/null 2>&1
"$ADB" shell bmgr enabled 2>/dev/null | grep -q 'currently enabled' || {
    fail "Backup Manager is disabled" "With it disabled EVERYTHING is refused, including the control."
    exit 1
}

# ── 1. The manifest opt-out, as the OS itself sees it ───────────────────────────────────────────
echo "1/4 · the OS's own view of the installed package…"
if "$ADB" shell dumpsys package "$PKG" 2>/dev/null | grep -m1 'flags=\[' | grep -q 'ALLOW_BACKUP'; then
    fail "the installed package carries ALLOW_BACKUP" "android:allowBackup=\"false\" did not take effect."
fi

# ── 2. Cloud transport: control must succeed, ours must be refused ──────────────────────────────
echo "2/4 · cloud transport, with control…"
"$ADB" shell bmgr transport "$CLOUD" >/dev/null 2>&1
"$ADB" shell bmgr backupnow "$CONTROL_PKG" 2>&1 | grep -q "$CONTROL_PKG with result: Success" || {
    fail "the CONTROL could not be backed up over the cloud transport" \
         "The apparatus is broken; our package's refusal would prove nothing. Not concluding."
    exit 1
}
"$ADB" shell bmgr backupnow "$PKG" 2>&1 | grep -q "$PKG with result: Backup is not allowed" || {
    fail "our package was NOT refused by the cloud transport" "Cloud backup is open."
}

# ── 3. D2D transport: the path allowBackup does not cover ───────────────────────────────────────
echo "3/4 · device-to-device transport, with control…"
"$ADB" shell bmgr transport "$D2D" >/dev/null 2>&1
"$ADB" shell bmgr backupnow "$CONTROL_PKG" 2>&1 | grep -q "$CONTROL_PKG with result: Success" || {
    fail "the CONTROL could not be backed up over D2D" "Apparatus broken; not concluding."
    exit 1
}
# THE DECISIVE SIGNAL IS BYTES, NOT THE VERDICT STRING. Measured on this emulator, the same
# excluded package answered "Backup is not allowed" in one state and "Transport rejected package
# because it wasn't able to process it at the time" in another, both with zero bytes. Asserting the
# exact string would make this test brittle AND could mask a regression when the wording changes.
# What never varied: a package whose data IS transferred emits `with progress: <n>/<m>` lines.
d2d_out=$(d2d_backup "$PKG")
if echo "$d2d_out" | grep -q "$PKG with progress:"; then
    fail "DATA WAS EXTRACTED over device-to-device transfer" \
         "$(echo "$d2d_out" | grep "$PKG with progress:" | tail -1) — <device-transfer> is not excluding it."
fi
if echo "$d2d_out" | grep -q "$PKG with result: Success"; then
    fail "the D2D transport accepted our package" "It should be excluded by <device-transfer>."
fi

# ── 4. Leave the device as we found it ──────────────────────────────────────────────────────────
echo "4/4 · restoring the default transport…"
"$ADB" shell bmgr transport com.google.android.gms/.backup.BackupTransportService >/dev/null 2>&1

# ── OPTIONAL: manufacture the positive control from our own app ────────────────────────────────
# The default run proves "no bytes leave". That is only evidence if this check COULD have seen bytes
# — and the proof of that is historical: before <device-transfer> existed, this exact package
# emitted `progress: 3072/1024` here. `--with-live-control` reproduces that on demand: it removes
# the section, rebuilds, installs, measures (bytes MUST appear), then restores everything.
if [ $LIVE_CONTROL -eq 1 ] && [ $FAIL -eq 0 ]; then
    echo ""
    echo "LIVE CONTROL · temporarily removing <device-transfer> to prove this check can see a leak…"
    RULES="androidApp/src/main/res/xml/data_extraction_rules.xml"
    BACKUP_COPY=$(mktemp)
    cp "$RULES" "$BACKUP_COPY"
    # Restore no matter how this exits — an interrupted run must never leave the hole open.
    trap 'cp "$BACKUP_COPY" "$RULES"; rm -f "$BACKUP_COPY"; echo "(rules restored)"' EXIT INT TERM

    python3 - "$RULES" <<'PY'
import re, sys, pathlib
p = pathlib.Path(sys.argv[1])
p.write_text(re.sub(r'<device-transfer>.*?</device-transfer>', '', p.read_text(), flags=re.S))
PY
    ./gradlew :androidApp:installDebug -q 2>&1 | grep -E "FAILED|error:" | head -3
    "$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 3
    # Step 4 already restored the DEFAULT transport, so the control has to select D2D again.
    # Forgetting this measured the cloud path instead and reported "no leak" — the control caught
    # a bug in the control itself, which is the argument for having one.
    "$ADB" shell bmgr transport "$D2D" >/dev/null 2>&1
    leak_out=$(d2d_backup "$PKG")
    if echo "$leak_out" | grep -q "$PKG with progress:"; then
        echo "  control OK — with the section removed, D2D extracted: $(echo "$leak_out" | grep "$PKG with progress:" | tail -1)"
    else
        fail "the LIVE CONTROL saw no leak either" \
             "This check cannot distinguish excluded from leaking, so its negative proves nothing."
    fi

    cp "$BACKUP_COPY" "$RULES"; rm -f "$BACKUP_COPY"; trap - EXIT INT TERM
    ./gradlew :androidApp:installDebug -q 2>&1 | grep -E "FAILED|error:" | head -3
    "$ADB" shell bmgr transport com.google.android.gms/.backup.BackupTransportService >/dev/null 2>&1
    echo "  rules restored, app reinstalled, default transport back."
fi

if [ $FAIL -eq 0 ]; then
    echo ""
    echo "no-backup: PROVEN on device — refused by BOTH the cloud and the device-to-device transport,"
    echo "           with a positive control succeeding on each in the same session."
else
    exit 1
fi
