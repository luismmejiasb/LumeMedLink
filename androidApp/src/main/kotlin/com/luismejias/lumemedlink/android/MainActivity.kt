package com.luismejias.lumemedlink.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.luismejias.lumemedlink.app.App
import com.luismejias.lumemedlink.core.input.denyAutofillExport
import com.luismejias.lumemedlink.core.input.denyContentCapture

/**
 * The Android half of the shell. Everything it shows comes from `commonMain`.
 *
 * A [FragmentActivity] rather than a bare ComponentActivity because BiometricPrompt hosts itself
 * in one (F4, ADR-0011). If this base class ever changes back, the tier-2 gate resolves to null
 * and every session ends at the lock screen — loudly, never silently open.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE app-wide (ADR-0010): the whole app is personal-data-adjacent — an agenda
        // reveals health (threat model T2) — so screenshots and the recents thumbnail are blocked
        // for every screen, set once here so no screen can forget it. Android CAN block these;
        // iOS cannot (asymmetry 1), where the Compose privacy cover carries the protection.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Tapjacking: drop touches delivered while another window overlays ours.
        window.decorView.filterTouchesWhenObscured = true
        // The OS structure-export channels (F3 reopened, ADR-0024) — a different mechanism from
        // FLAG_SECURE and NOT covered by it. Compose publishes an autofill node for every text
        // field on its own initiative, so the structure went to whichever autofill service the
        // user installed. Two calls rather than one wrapper because the two channels are two
        // mechanisms: the first closes a hole, the second keeps an already-closed one shut.
        // It must be the DECOR view — on the Compose view the assignment is silently discarded
        // (core/input/StructureExport.kt says why).
        window.decorView.denyAutofillExport()
        denyContentCapture()
        setContent { App() }
    }
}
