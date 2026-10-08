#!/bin/zsh
# Device verifier: are the personal data a person TYPES hidden from accessibility services that are not
# accessibility tools, while labels and guidance stay readable? (ADR-0042, task 0021, author's decision B)
#
# Android 14+ only: `isAccessibilityDataSensitive` is what the platform honours. The verifier shows a
# TEMPORARY SensitiveTextField on the login screen with synthetic data, and a TEMPORARY device test reads the
# live tree through UiAutomation (the field is drawn on the sign-in screen, under the app's name). Three things must hold:
#   1. the CONTROL — a node of ours marked directly — reads sensitive: the instrument can see the property;
#   2. the typed value reads sensitive;
#   3. the label reads NOT sensitive (the author: labels and guidance stay readable).
# Measured 2026-10-07: 1 and 3 hold, 2 does NOT — a mark on the kit field's root never reaches its inner
# text node. It needs a kit parameter; until it lands this verifier is red, which is the honest answer.
#
# Usage: Scripts/verify-accessibility-sensitivity.sh   (emulator API 34+, ANDROID_SERIAL set; edits and
#        restores two files; pass -Plume.kit.path via LUME_KIT_PATH when the kit's tree is being edited)
# through UiAutomation, runs it with the mark (production) and without it (control). Restores on exit.
set -u
R=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd $R || exit 1
T=$(mktemp -d "${TMPDIR:-/tmp}/lume-a11y.XXXXXX") || exit 1
KIT_ARG=${LUME_KIT_PATH:+-Plume.kit.path=$LUME_KIT_PATH}
SCREENS=composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/features/auth/login/LoginScreen.kt
FIELD=composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/input/SensitiveTextField.kt
PROBE=composeApp/src/androidDeviceTest/kotlin/com/luismejias/lumemedlink/core/input/A11yProbe.kt
cp $SCREENS $T/Screens.kt.orig; cp $FIELD $T/SensitiveTextField.kt.orig
restore() { cp $T/Screens.kt.orig $R/$SCREENS; cp $T/SensitiveTextField.kt.orig $R/$FIELD; rm -f $R/$PROBE; rm -rf $T; }
trap restore EXIT
python3 - <<'PY'
p="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/features/auth/login/LoginScreen.kt"; s=open(p).read()
old='''        LumeDivider()'''
assert old in s
s=s.replace(old, old+'''
        com.luismejias.lumemedlink.core.input.SensitiveTextField(
            value = "synthetic-555-0100",
            onValueChange = {},
            purpose = com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose.PERSONAL_DATA,
            label = "Etiqueta-orientativa",
            placeholder = "Ayuda-orientativa",
        )
        androidx.compose.foundation.text.BasicText(
            "Control-marcado",
            androidx.compose.ui.Modifier.semantics { isSensitiveData = true },
        )''',1)
s=s.replace("package com.luismejias.lumemedlink.features.auth.login\n\n","package com.luismejias.lumemedlink.features.auth.login\n\nimport androidx.compose.ui.semantics.isSensitiveData\nimport androidx.compose.ui.semantics.semantics\n",1)
open(p,"w").write(s)
PY
cat > $PROBE <<'KT'
package com.luismejias.lumemedlink.core.input

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

// TEMPORARY — written and removed by tmp/lml/measure-0021.sh. Prints, never asserts.
@RunWith(AndroidJUnit4::class)
class A11yProbe {
    @Test
    fun dump() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        Thread.sleep(1500)
        val roots = listOfNotNull(ui.rootInActiveWindow)
        println("LUME-0021 roots=${roots.size} pkg=${roots.firstOrNull()?.packageName}")
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val text = listOfNotNull(n.text?.toString(), n.contentDescription?.toString(), n.hintText?.toString())
                .joinToString("|")
            if (text.isNotEmpty() || n.isEditable) {
                println("LUME-0021 editable=${n.isEditable} sensitive=${n.isAccessibilityDataSensitive} text=$text")
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        roots.forEach { walk(it, 0) }
    }
}
KT
run() {
  label=$1
  ./gradlew --console=plain $KIT_ARG :androidApp:assembleDebug :composeApp:assembleAndroidTest > $T/0021-$label-build.log 2>&1 ||
    { echo "BUILD_FAILED $label"; grep -E "^e: |What went wrong" -A3 $T/0021-$label-build.log | head; return 1; }
  adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk >/dev/null
  adb install -r -t composeApp/build/outputs/apk/androidTest/composeApp-androidTest.apk >/dev/null
  adb shell am force-stop com.luismejias.lumemedlink
  adb shell am start -W -n com.luismejias.lumemedlink/.android.MainActivity >/dev/null
  sleep 3
  adb logcat -c
  adb shell am instrument -w -e class com.luismejias.lumemedlink.core.input.A11yProbe \
    com.luismejias.lumemedlink.test/androidx.test.runner.AndroidJUnitRunner > $T/0021-$label-instr.log 2>&1
  echo "== $label"; adb logcat -d | grep -o "LUME-0021.*" | sort -u
}
run production || exit 1
OUT=$(adb logcat -d | grep -o "LUME-0021.*" | sort -u)
FAIL=0
echo "$OUT" | grep -q "sensitive=true text=Control-marcado" ||
    { echo "FAIL the control is not seen as sensitive: the instrument cannot see the property (nothing below means anything)"; exit 1; }
echo "$OUT" | grep -q "editable=true sensitive=true text=synthetic-555-0100" ||
    { echo "FAIL the typed value is readable by every accessibility service (ADR-0042)"; FAIL=1; }
echo "$OUT" | grep -q "sensitive=false text=Etiqueta-orientativa" ||
    { echo "FAIL the label is hidden too: labels and guidance must stay readable (ADR-0042)"; FAIL=1; }
[ $FAIL -eq 0 ] && echo "verify-accessibility-sensitivity: OK" || exit 1
