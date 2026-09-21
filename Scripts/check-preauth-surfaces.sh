#!/bin/sh
# Pre-auth surface gate (F2, ADR-0012) — the places that show something to someone who has NOT
# unlocked the app: the lock screen, the notification shade, the launcher, the home screen.
#
# Why a gate and not a rule: every pattern below is one line, looks harmless in review, and leaks
# to a person who never authenticated. "Recordatorio: control de diabetes" as a notification is a
# health disclosure under Ley 21.719 (threat model T2, which this app ranks FIRST because a
# patient's phone is used by their family).
#
# The rule this enforces is NOT "no notifications ever" — it is: no surface exists yet, and when
# one arrives it arrives through a reviewed seam carrying `PushSignal`, which has no text field to
# leak (shared/PushSignal.kt). Until that seam exists, these APIs have no legitimate caller.
#
# Rehearsed against bait before being trusted.
#
# Usage: Scripts/check-preauth-surfaces.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

SRC="composeApp/src androidApp/src"
# The iOS host was outside every scan here, so the whole iOS half of this rule was unwritten: a
# notification request, a widget, a Handoff activity or a UserDefaults write in Swift passed
# untouched (audit, ADR-0029). `scan` below covers .kt; SWIFT_SRC is checked separately because the
# APIs have different names, not because the rule is different.
SWIFT_SRC="iosApp"
MANIFEST="androidApp/src/main/AndroidManifest.xml"
FAIL=0

fail() {
    FAIL=1
    echo ""
    echo "FAIL $1"
    echo "  $2"
    [ -n "${3:-}" ] && echo "$3" | sed 's/^/    /'
}

# Skips comment lines: a KDoc explaining the rule must not be mistaken for breaking it.
scan() {
    grep -rnE "$1" $SRC 2>/dev/null | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\*|/\*)' || true
}

# ── The notification shade / lock screen ────────────────────────────────────────────────────────
hits=$(scan 'NotificationCompat|Notification\.Builder|NotificationManagerCompat|\.notify\(|createNotificationChannel')
if [ -n "$hits" ]; then
    fail "pre-auth: notification API in use without a reviewed seam" \
         "A notification is content on a locked screen (§8.5). Push arrives as PushSignal through its own slice." \
         "$hits"
fi
hits=$(scan 'setContentText|setContentTitle|setBigContentTitle|setTicker|UNMutableNotificationContent|UNNotificationRequest')
if [ -n "$hits" ]; then
    fail "pre-auth: notification TEXT being composed" \
         "Visible copy comes from PushSignal.displayCopyKey (app-owned), never from a payload or a record." \
         "$hits"
fi
hits=$(scan 'VISIBILITY_PUBLIC|setVisibility\(1\)')
if [ -n "$hits" ]; then
    fail "pre-auth: notification declared publicly visible" \
         "Public visibility renders the content on the lock screen verbatim (§8.5)." "$hits"
fi

# ── The launcher: dynamic shortcuts carry their label into a surface nobody authenticates for ──
hits=$(scan 'ShortcutManager|ShortcutInfo|setDynamicShortcuts|pushDynamicShortcut')
if [ -n "$hits" ]; then
    fail "pre-auth: launcher shortcut API in use" \
         "A shortcut label ('Cita con Dr. X') sits in the launcher, visible with the app locked (ADR-0012)." \
         "$hits"
fi

# ── The home screen: widgets render app content with no session at all ─────────────────────────
hits=$(scan 'AppWidgetProvider|android\.appwidget|RemoteViews|WidgetKit|WidgetConfiguration')
if [ -n "$hits" ]; then
    fail "pre-auth: widget API in use" \
         "A widget draws personal data on the home screen with the app locked (ADR-0012: no widgets in v1)." \
         "$hits"
fi

# ── Drawing the app OVER the lock screen ────────────────────────────────────────────────────────
hits=$(scan 'setShowWhenLocked|showWhenLocked|setTurnScreenOn|FLAG_SHOW_WHEN_LOCKED|FLAG_DISMISS_KEYGUARD')
if [ -n "$hits" ]; then
    fail "pre-auth: the app asks to appear over the lock screen" \
         "That places app content in front of someone who never unlocked the device (threat model T2)." \
         "$hits"
fi

# ── The manifest: declared surfaces and permissions the app has no use for yet ─────────────────
# Comments are blanked by a tokenizer, not filtered by line: `grep -v '<!--'` DROPS any line that
# mentions the marker, so a real declaration with a trailing comment escaped the check entirely,
# and the inner lines of a multi-line comment were never recognised at all (ADR-0029). Blanking
# preserves line numbers, so the hits below still point at the real line.
manifest_code() { python3 Scripts/lib/uncomment.py --lang xml "$MANIFEST" 2>/dev/null; }
if [ -f "$MANIFEST" ]; then
    MANIFEST_CODE=$(manifest_code)
    hits=$(printf '%s\n' "$MANIFEST_CODE" | grep -nE 'POST_NOTIFICATIONS|APPWIDGET_UPDATE|appwidget-provider|android\.permission\.SYSTEM_ALERT_WINDOW' || true)
    if [ -n "$hits" ]; then
        fail "pre-auth: a pre-auth surface is declared in the manifest" \
             "No notification permission, widget provider or overlay window until its slice exists (ADR-0012)." \
             "$hits"
    fi
    hits=$(printf '%s\n' "$MANIFEST_CODE" | grep -nE 'android:showWhenLocked|android:turnScreenOn' || true)
    if [ -n "$hits" ]; then
        fail "pre-auth: the manifest asks to show the app over the lock screen" \
             "Threat model T2: nothing of this app renders before authentication." "$hits"
    fi
fi

# ── The iOS host: the same rule, the other platform's names (ADR-0012) ─────────────────────────
# Comments and string literals stripped by the shared tokenizer, so a KDoc naming an API is prose
# (ADR-0029). Presence of ANY of these is the failure: none of these surfaces exists by decision,
# and the day one does it arrives with its slice and its ADR, not as a line in a delegate.
if [ -d "$SWIFT_SRC" ]; then
    SWIFT_FILES=$(find "$SWIFT_SRC" -name '*.swift' 2>/dev/null)
    swift_scan() {
        [ -n "$SWIFT_FILES" ] || return 0
        # shellcheck disable=SC2086
        python3 Scripts/lib/uncomment.py --lang c --strip-strings $SWIFT_FILES 2>/dev/null | grep -nE "$1" || true
    }

    hits=$(swift_scan 'UNUserNotificationCenter|UNMutableNotificationContent|UNNotificationRequest|registerForRemoteNotifications')
    if [ -n "$hits" ]; then
        fail "pre-auth: the iOS host builds or registers for notifications" \
             "No pre-auth surface exists until its slice does, and a payload is content on a locked screen (ADR-0012, §8.5)." "$hits"
    fi
    hits=$(swift_scan 'WidgetKit|WidgetConfiguration|IntentConfiguration|StaticConfiguration')
    if [ -n "$hits" ]; then
        fail "pre-auth: a widget surface in the iOS host" \
             "A widget renders before anyone authenticates (threat model T2, ADR-0012)." "$hits"
    fi
    hits=$(swift_scan 'NSUserActivity|CSSearchableItem|CSSearchableIndex|isEligibleForHandoff|isEligibleForSearch')
    if [ -n "$hits" ]; then
        fail "pre-auth: Handoff or Spotlight indexing in the iOS host" \
             "Both publish app content outside the app — Spotlight to the lock screen, Handoff to another device (ADR-0012)." "$hits"
    fi
    hits=$(swift_scan 'UserDefaults|NSUbiquitousKeyValueStore')
    if [ -n "$hits" ]; then
        fail "pre-auth: plain or cloud key-value storage in the iOS host" \
             "The Kotlin half of this rule is P3 in check-forbidden-patterns; Swift had no half at all (§8.4, ADR-0005)." "$hits"
    fi
fi

if [ $FAIL -eq 0 ]; then
    echo "pre-auth-surfaces: OK"
else
    exit 1
fi
