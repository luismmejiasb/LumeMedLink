package com.luismejias.lumemedlink.app

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEmptyStateLook
import cl.lume.uicomposer.components.LumeEmptyStateTone
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.session.SessionEntryRefusal
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.session_refused_action
import com.luismejias.lumemedlink.resources.session_refused_biometrics_body
import com.luismejias.lumemedlink.resources.session_refused_biometrics_title
import com.luismejias.lumemedlink.resources.session_refused_body
import com.luismejias.lumemedlink.resources.session_refused_title
import org.jetbrains.compose.resources.stringResource

/**
 * Why the session was not begun (ADR-0066). Without biometrics there is no session (ADR-0037, the author's call of
 * 2026-10-15: they are required), and the person is told what to turn on; every other refusal left nothing behind and
 * says so. The one action goes back to the sign-in.
 */
@Composable
internal fun SessionRefusedScreen(reason: SessionEntryRefusal, onBack: () -> Unit) {
    val biometrics = reason == SessionEntryRefusal.TIER2_UNAVAILABLE
    LumeContainer(alignment = LumeContainerAlignment.Center) {
        LumeEmptyState(
            look = LumeEmptyStateLook.Tiled(LumeEmptyStateTone.Error),
            icon = if (biometrics) LumeIcon.Shield else LumeIcon.Alert,
            title = stringResource(
                if (biometrics) Res.string.session_refused_biometrics_title else Res.string.session_refused_title,
            ),
            message = stringResource(
                if (biometrics) Res.string.session_refused_biometrics_body else Res.string.session_refused_body,
            ),
            action = LumeButtonContent.Title(stringResource(Res.string.session_refused_action)),
            onAction = onBack,
        )
    }
}

/** While the session is being begun: nothing of the app exists before it does, and the wait is too short to draw. */
@Composable
internal fun SessionEnteringScreen() {
    LumeContainer(alignment = LumeContainerAlignment.Center) {}
}
