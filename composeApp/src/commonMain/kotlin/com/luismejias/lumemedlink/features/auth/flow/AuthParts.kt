package com.luismejias.lumemedlink.features.auth.flow

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeIconTile
import cl.lume.uicomposer.components.LumeIconTileSize
import cl.lume.uicomposer.components.LumeResult
import cl.lume.uicomposer.components.LumeResultButton
import cl.lume.uicomposer.components.LumeResultKind
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon

/** The head every step after the sign-in shares in LumeMed: a glyph tile, a title, one line of why. */
@Composable
internal fun AuthHeader(icon: LumeIcon, title: String, body: String) {
    LumeStack(alignment = LumeStackAlignment.Center, spacing = { medium }) {
        LumeIconTile(icon = icon, size = LumeIconTileSize.Large)
        LumeText(text = title, style = LumeTextStyle.HeadingLarge)
        LumeText(text = body, color = LumeTextColor.Secondary)
    }
}

/**
 * A step's primary action: the kit's result button, which spins while the step runs and flashes how it
 * ended (LumeMed's `LumeResultButton` use). A refusal flashes its message on the button; a failure the
 * person did not cause flashes briefly and the flow's alert says the rest; [retryEpoch] re-runs the step
 * when that alert's retry is chosen.
 */
@Composable
internal fun AuthPrimaryButton(
    title: String,
    loadingMessage: String,
    enabled: Boolean,
    retryEpoch: Int,
    run: suspend () -> StepOutcome,
) {
    LumeResultButton(
        title = title,
        loadingMessage = loadingMessage,
        state = if (enabled) LumeFieldState.Enabled else LumeFieldState.Disabled,
        runTrigger = retryEpoch,
        onRun = {
            when (val outcome = run()) {
                StepOutcome.Advanced -> LumeResult(LumeResultKind.Success, AuthCopy.DONE)
                is StepOutcome.Rejected -> LumeResult(LumeResultKind.Error, outcome.message)
                StepOutcome.Blocked -> LumeResult(LumeResultKind.Error, AuthCopy.FAILED_SHORT)
                is StepOutcome.Retry -> LumeResult(LumeResultKind.Warning, outcome.message)
            }
        },
    )
}
