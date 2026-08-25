package com.luismejias.lumemedlink.core.input

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.contentcapture.ContentCaptureManager
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Asks a REAL Android runtime whether the autofill exclusion actually defeats Compose's hardcoded
 * opt-in (F3 reopened, ADR-0024).
 *
 * ## What the stand-in is, and why it is honest
 *
 * The view under test is not `AndroidComposeView` — it is a [View] subclass that reproduces the
 * one thing about it that matters here: `getImportantForAutofill()` overridden to a hardcoded
 * `IMPORTANT_FOR_AUTOFILL_YES`, ignoring its own setter. That is verbatim what Compose 1.11.2 does
 * (`AndroidComposeView.android.kt`, `override fun getImportantForAutofill(): Int { return
 * IMPORTANT_FOR_AUTOFILL_YES }`), pinned by the lockfile.
 *
 * Using the stand-in rather than a live Compose hierarchy is a deliberate trade, stated so nobody
 * has to guess: attaching a real `ComposeView` needs an Activity, a lifecycle owner and a new test
 * dependency, and all of that would test *composition plumbing* while the property at stake is a
 * platform rule — how `View.isImportantForAutofill()` resolves a hardcoded child against an
 * excluded ancestor. This test proves that rule on the device. That Compose's view really carries
 * the hardcoded override is proven by its source, at the version the lockfile pins.
 *
 * ## The control
 *
 * Every assertion here is worthless without its opposite, so the first assertion of each test is
 * the control: the stand-in IS exported before the fix. If Android ever changed the ancestor rule,
 * the control would keep passing and the fix assertion would fail — which is the failure mode a
 * test is for.
 *
 * Runs with `./gradlew :composeApp:connectedAndroidTest` (a booted device or emulator required).
 */
@RunWith(AndroidJUnit4::class)
class StructureExportTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Reproduces the single relevant behaviour of `AndroidComposeView`. */
    private class HardcodedYesView(context: Context) : View(context) {
        override fun getImportantForAutofill(): Int = IMPORTANT_FOR_AUTOFILL_YES
    }

    private fun hierarchy(): Pair<ViewGroup, View> {
        val root = FrameLayout(context)
        val child = HardcodedYesView(context)
        root.addView(child)
        return root to child
    }

    @Test
    fun theSetterOnTheComposeViewIsSilentlyDiscarded() {
        val (_, child) = hierarchy()

        // The obvious fix, applied to the obvious place.
        child.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        assertEquals(
            View.IMPORTANT_FOR_AUTOFILL_YES,
            child.importantForAutofill,
            "The assignment reported no error and changed nothing. This is why the exclusion is " +
                "applied to the decor view instead — a fix applied here would look applied.",
        )
    }

    @Test
    fun anExcludedAncestorOverridesTheHardcodedOptIn() {
        val (root, child) = hierarchy()

        assertTrue(
            child.isImportantForAutofill,
            "CONTROL: without the exclusion the view IS part of the autofill structure. If this " +
                "ever fails, the hole this test guards has moved and the assertion below is void.",
        )

        root.denyAutofillExport()

        assertFalse(
            child.isImportantForAutofill,
            "The ancestor exclusion must beat the child's hardcoded YES (ADR-0024). If this fails, " +
                "every text field in the app is again a node in the structure handed to the " +
                "user's autofill service.",
        )
    }

    @Test
    fun theExclusionReachesGrandchildrenNotJustDirectChildren() {
        val root = FrameLayout(context)
        val middle = FrameLayout(context)
        val leaf = HardcodedYesView(context)
        root.addView(middle)
        middle.addView(leaf)

        assertTrue(leaf.isImportantForAutofill, "CONTROL: exported before the exclusion.")

        root.denyAutofillExport()

        assertFalse(
            leaf.isImportantForAutofill,
            "NO_EXCLUDE_DESCENDANTS must cover the whole subtree, not one level — the real " +
                "hierarchy puts several groups between the decor view and the Compose view.",
        )
    }

    @Test
    fun contentCaptureEndsUpDisabledForThisApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val manager = context.getSystemService(ContentCaptureManager::class.java) ?: return

        // This control was written as "probably worthless" and then measured, which is the whole
        // point of measuring. On the Pixel 9 emulator at API 37 the channel IS enabled before the
        // call — the image ships Android System Intelligence as the content-capture service — and
        // false after it. So on this device the before/after is real.
        //
        // The honest limit survives, narrower: on a device with NO content-capture service the
        // value is already false and the control cannot distinguish "we disabled it" from "it was
        // never on". The failure message reports which case the run hit rather than leaving the
        // reader to assume the good one.
        val wasEnabledBefore = manager.isContentCaptureEnabled

        context.denyContentCapture()

        assertFalse(
            manager.isContentCaptureEnabled,
            "Content capture must be off for this app (ADR-0024). Enabled beforehand: " +
                "$wasEnabledBefore — if that is false this run had no control and proves only " +
                "that the call is harmless.",
        )
    }
}
