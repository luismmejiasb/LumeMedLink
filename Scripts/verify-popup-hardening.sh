#!/bin/zsh
# Device verifier: do the kit's popups sit under the window hardening the app gives its own window?
# (task 0014, ADR-0010 tapjacking + FLAG_SECURE)
#
# `MainActivity` hardens the activity's window: FLAG_SECURE, and `filterTouchesWhenObscured` on the decor view.
# The kit's LumeAlert / LumeMenu / LumePickerField draw through Compose's `Popup`, which on Android is ANOTHER
# window — under neither, unless something carries them over. Measured here, with controls, on the emulator:
#
#   A. FLAG_SECURE. `screencap` while the flow's alert is open: black means the popup is secure.
#      Control: the launcher, captured the same way, must NOT be black — or "black" proves nothing.
#   B. Tapjacking. A half-transparent overlay window from ANOTHER app (the test APK) covers the screen, and a tap
#      is injected through it on a button:
#      B0 control — the main window WITHOUT the overlay: tapping "Mostrar contraseña" must flip it (the instrument can tap).
#      B1 control — the main window: tapping "Mostrar contraseña" through the overlay must NOT flip it.
#      B2 control — the alert WITHOUT the overlay: tapping "Cerrar" must close it (the instrument can tap).
#      B3 the alert WITH the overlay: if "Cerrar" still closes it, the popup is tapjackable.
#
# The autofill half of the task is not measured, because it cannot happen with this kit: none of its popups holds a
# text field (read off the kit's sources; re-check this if a popup ever gains one).
#
# Edits two files temporarily and restores them; adds a temporary test (and the overlay permission to the TEST
# apk only). Usage: Scripts/verify-popup-hardening.sh  (emulator, ANDROID_SERIAL; LUME_KIT_PATH optional)
set -u
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd $R || exit 1
T=$(mktemp -d "${TMPDIR:-/tmp}/lume-popup.XXXXXX") || exit 1
KIT_ARG=${LUME_KIT_PATH:+-Plume.kit.path=$LUME_KIT_PATH}
HOST=composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/features/auth/flow/AuthFlowHost.kt
PROBE=composeApp/src/androidDeviceTest/kotlin/com/luismejias/lumemedlink/app/PopupHardeningProbe.kt
TMANIFEST=composeApp/src/androidDeviceTest/AndroidManifest.xml
PKG=com.luismejias.lumemedlink
cp $HOST $T/host.orig
restore() { cp $T/host.orig $R/$HOST; rm -f $R/$PROBE $R/$TMANIFEST; rm -rf $T; }
trap restore EXIT

cat > $TMANIFEST <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<!-- TEMPORARY — written and removed by Scripts/verify-popup-hardening.sh. The overlay is the attacker's window. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
</manifest>
XML
cat > $PROBE <<'KT'
package com.luismejias.lumemedlink.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

// TEMPORARY — written and removed by Scripts/verify-popup-hardening.sh. Prints, never asserts.
@RunWith(AndroidJUnit4::class)
class PopupHardeningProbe {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui = instrumentation.uiAutomation

    @Test
    fun tapThrough() {
        val args = InstrumentationRegistry.getArguments()
        // A key, not the text: `adb shell` re-splits an argument with spaces.
        val target = when (args.getString("target")) {
            // Its label turns into "Ocultar contraseña" when tapped, so "gone" means "the tap landed".
            // NOT the recovery link: the screen it opens is titled with the same words.
            "reveal" -> "Mostrar contraseña"
            else -> "Cerrar"
        }
        val withOverlay = args.getString("overlay") == "true"
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        Thread.sleep(1000)
        val node = find(target) ?: run { println("LUME-0014 target-missing=$target"); return }
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val wm = instrumentation.context.getSystemService(WindowManager::class.java)
        val overlay = View(instrumentation.context).apply { setBackgroundColor(Color.argb(128, 255, 0, 0)) }
        if (withOverlay) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply { alpha = 0.5f }
            instrumentation.runOnMainSync { wm.addView(overlay, params) }
            Thread.sleep(800)
        }
        tap(bounds.exactCenterX(), bounds.exactCenterY())
        Thread.sleep(1500)
        println("LUME-0014 target=$target overlay=$withOverlay still-there=${find(target) != null}")
        if (withOverlay) instrumentation.runOnMainSync { wm.removeView(overlay) }
    }

    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN to t, MotionEvent.ACTION_UP to t + 60).forEach { (action, at) ->
            val e = MotionEvent.obtain(t, at, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            ui.injectInputEvent(e, true)
            e.recycle()
        }
    }

    private fun find(text: String): AccessibilityNodeInfo? {
        fun walk(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            n ?: return null
            if (n.text?.toString() == text || n.contentDescription?.toString() == text) return n
            for (i in 0 until n.childCount) walk(n.getChild(i))?.let { return it }
            return null
        }
        return ui.windows.firstNotNullOfOrNull { walk(it.root) }
    }
}
KT

build() {
  ./gradlew --console=plain $KIT_ARG :androidApp:assembleDebug :composeApp:assembleAndroidTest > $T/build-$1.log 2>&1 ||
    { echo "BUILD_FAILED $1"; grep -E "^e: |What went wrong" -A3 $T/build-$1.log | head; exit 1; }
  adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk >/dev/null
  adb install -r -t composeApp/build/outputs/apk/androidTest/composeApp-androidTest.apk >/dev/null
  adb shell appops set $PKG.test SYSTEM_ALERT_WINDOW allow
}
launch() { adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.android.MainActivity >/dev/null; sleep 4; }
probe() {
  adb logcat -c
  adb shell am instrument -w -e class $PKG.app.PopupHardeningProbe -e target "$1" -e overlay "$2" \
    $PKG.test/androidx.test.runner.AndroidJUnitRunner > $T/instr.log 2>&1
  adb logcat -d | grep -o "LUME-0014.*" | tail -1
}
luma() {  # mean luma of a screencap, 0..255
  adb exec-out screencap -p > $T/shot.png
  python3 - $T/shot.png <<'PY'
import sys, zlib, struct
d=open(sys.argv[1],'rb').read()
w,h=struct.unpack('>II',d[16:24]); ct=d[25]
idat=b''; i=8
while i<len(d):
    n=struct.unpack('>I',d[i:i+4])[0]; t=d[i+4:i+8]
    if t==b'IDAT': idat+=d[i+8:i+8+n]
    i+=12+n
raw=zlib.decompress(idat); bpp=4 if ct==6 else 3; stride=w*bpp; prev=bytearray(stride); tot=0; cnt=0; pos=0
for y in range(h):
    f=raw[pos]; line=bytearray(raw[pos+1:pos+1+stride]); pos+=1+stride
    for x in range(stride):
        a=line[x-bpp] if x>=bpp else 0; b=prev[x]; c=prev[x-bpp] if x>=bpp else 0
        if f==1: line[x]=(line[x]+a)&255
        elif f==2: line[x]=(line[x]+b)&255
        elif f==3: line[x]=(line[x]+((a+b)>>1))&255
        elif f==4:
            p=a+b-c; pa,pb,pc=abs(p-a),abs(p-b),abs(p-c)
            line[x]=(line[x]+(a if pa<=pb and pa<=pc else b if pb<=pc else c))&255
    prev=line
    if y%8==0:
        for x in range(0,w,8):
            r,g,bl=line[x*bpp:x*bpp+3]; tot+=0.299*r+0.587*g+0.114*bl; cnt+=1
print(round(tot/cnt,1))
PY
}

FAIL=0
# ── Control build: the login screen, no alert ─────────────────────────────────────────────────────────
build control
adb shell input keyevent KEYCODE_HOME; sleep 2
L_LAUNCHER=$(luma); echo "control  · launcher screencap luma = $L_LAUNCHER (must be > 0)"
launch
B0=$(probe reveal false); echo "B0 control · main window, no overlay: $B0"
echo "$B0" | grep -q "still-there=false" || { echo "FAIL B0: the instrument could not flip "Mostrar contraseña" without an overlay — B1 would mean nothing"; exit 1; }
launch
B1=$(probe reveal true); echo "B1 control · main window, through overlay: $B1"
echo "$B1" | grep -q "still-there=true" || { echo "FAIL B1: the main window took a touch through the overlay — the control itself is broken"; exit 1; }

# ── Alert build: the flow's alert open at launch ──────────────────────────────────────────────────────
python3 - <<'PY'
p="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/features/auth/flow/AuthFlowHost.kt"; s=open(p).read()
old="    val top = routes.last()\n"
assert old in s
s=s.replace(old, old+"    androidx.compose.runtime.LaunchedEffect(Unit) { flow.blockedBy(com.luismejias.lumemedlink.core.auth.AuthFailure.Unavailable) }\n",1)
open(p,"w").write(s)
PY
build alert
launch
L_ALERT=$(luma); echo "A        · alert open, screencap luma = $L_ALERT"
B2=$(probe close false); echo "B2 control · alert, no overlay: $B2"
echo "$B2" | grep -q "still-there=false" || { echo "FAIL B2: the instrument could not close the alert without an overlay — nothing below means anything"; exit 1; }
launch
B3=$(probe close true); echo "B3        · alert, through overlay: $B3"

python3 -c "import sys; sys.exit(0 if float('$L_LAUNCHER') > 5 else 1)" || { echo "FAIL control: the launcher captured black; screencap cannot tell"; exit 1; }
python3 -c "import sys; sys.exit(0 if float('$L_ALERT') < 1 else 1)" || { echo "FAIL A: the alert's window is captured — the popup is not under FLAG_SECURE"; FAIL=1; }
echo "$B3" | grep -q "still-there=true" || { echo "FAIL B3: a touch through another app's overlay reached the alert's button — the popup is tapjackable"; FAIL=1; }
[ $FAIL -eq 0 ] && echo "verify-popup-hardening: OK" || exit 1
