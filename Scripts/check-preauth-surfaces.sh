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
if [ -f "$MANIFEST" ]; then
    hits=$(grep -nE 'POST_NOTIFICATIONS|APPWIDGET_UPDATE|appwidget-provider|android\.permission\.SYSTEM_ALERT_WINDOW' "$MANIFEST" | grep -v '<!--' || true)
    if [ -n "$hits" ]; then
        fail "pre-auth: a pre-auth surface is declared in the manifest" \
             "No notification permission, widget provider or overlay window until its slice exists (ADR-0012)." \
             "$hits"
    fi
    hits=$(grep -nE 'android:showWhenLocked|android:turnScreenOn' "$MANIFEST" | grep -v '<!--' || true)
    if [ -n "$hits" ]; then
        fail "pre-auth: the manifest asks to show the app over the lock screen" \
             "Threat model T2: nothing of this app renders before authentication." "$hits"
    fi
fi

if [ $FAIL -eq 0 ]; then
    echo "pre-auth-surfaces: OK"
else
    exit 1
fi
