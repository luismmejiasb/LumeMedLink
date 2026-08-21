package com.luismejias.lumemedlink.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.luismejias.lumemedlink.app.App

/** The Android half of the shell. Everything it shows comes from `commonMain`. */
class MainActivity : ComponentActivity() {
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
        setContent { App() }
    }
}
