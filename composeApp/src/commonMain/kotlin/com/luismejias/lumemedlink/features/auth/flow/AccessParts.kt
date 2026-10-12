package com.luismejias.lumemedlink.features.auth.flow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeBrandInk
import cl.lume.uicomposer.components.LumeBrandJoiner
import cl.lume.uicomposer.components.LumeBrandLockup
import cl.lume.uicomposer.components.LumeBrandRun
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeGlassButton
import cl.lume.uicomposer.components.LumeIconTile
import cl.lume.uicomposer.components.LumeIconTileEmphasis
import cl.lume.uicomposer.components.LumeIconTileSize
import cl.lume.uicomposer.components.LumeResult
import cl.lume.uicomposer.components.LumeResultButton
import cl.lume.uicomposer.components.LumeResultKind
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextAlignment
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.session.Clock
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.app_name
import com.luismejias.lumemedlink.resources.brand_link
import com.luismejias.lumemedlink.resources.brand_lume
import com.luismejias.lumemedlink.resources.brand_med
import com.luismejias.lumemedlink.resources.common_back
import com.luismejias.lumemedlink.resources.common_done
import com.luismejias.lumemedlink.resources.common_failed_short
import com.luismejias.lumemedlink.resources.lumemedlink_icon
import com.luismejias.lumemedlink.resources.offline_body
import com.luismejias.lumemedlink.resources.offline_title
import com.luismejias.lumemedlink.resources.simbolo_enlace
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/** The tone of a screen's glyph tile: the accent, or the design's green for done and amber for wait. */
internal enum class AccessTone { ACCENT, SUCCESS, WAIT }

@Composable
internal fun AccessText.resolve(): String = when (this) {
    is AccessText.Plain -> stringResource(key)
    is AccessText.Counted -> pluralStringResource(key, count, count)
}

private suspend fun AccessText.resolveNow(): String = when (this) {
    is AccessText.Plain -> getString(key)
    is AccessText.Counted -> getPluralString(key, count, count)
}

/** The back disc at the top of a screen, where the route allows it. */
@Composable
internal fun AccessBack(onBack: (() -> Unit)?) {
    if (onBack != null) {
        // Full width so the disc sits at the leading edge even inside a centred column.
        LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.Start) {
            LumeGlassButton(
                icon = LumeIcon.ChevronLeft,
                contentDescription = stringResource(Res.string.common_back),
                onClick = onBack,
            )
        }
    }
}

/**
 * The brand over the sign-in screens (1a, 1b): the app icon and the name in its three inks, the link between «Med» and
 * «Link», as the handoff draws it (02-Assets/marca). A reader hears the name once.
 */
@Composable
internal fun AccessBrand() {
    LumeBrandLockup(
        mark = painterResource(Res.drawable.lumemedlink_icon),
        wordmark = listOf(
            LumeBrandRun(stringResource(Res.string.brand_lume)),
            LumeBrandRun(stringResource(Res.string.brand_med), LumeBrandInk.Hue(LumeTileTint.Teal)),
            LumeBrandRun(stringResource(Res.string.brand_link), LumeBrandInk.Accent),
        ),
        joiner = LumeBrandJoiner(painterResource(Res.drawable.simbolo_enlace), after = 1),
        contentDescription = stringResource(Res.string.app_name),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A glyph tile, a title, one line of why — the head of almost every access screen. */
@Composable
internal fun AccessHeader(icon: LumeIcon, title: String, body: String?, tone: AccessTone = AccessTone.ACCENT) {
    LumeStack(alignment = LumeStackAlignment.Center, spacing = { medium }) {
        LumeIconTile(
            icon = icon,
            size = LumeIconTileSize.Large,
            emphasis = when (tone) {
                AccessTone.ACCENT -> LumeIconTileEmphasis.Tint
                AccessTone.SUCCESS -> LumeIconTileEmphasis.Washed(LumeTileTint.Jade)
                AccessTone.WAIT -> LumeIconTileEmphasis.Washed(LumeTileTint.Amber)
            },
        )
        LumeText(text = title, style = LumeTextStyle.HeadingLarge, alignment = LumeTextAlignment.Center)
        if (body != null) {
            LumeText(text = body, color = LumeTextColor.Secondary, alignment = LumeTextAlignment.Center)
        }
    }
}

/** A form screen's title block, aligned to the leading edge (2a, 2b, 3c). */
@Composable
internal fun AccessTitle(title: String, body: String) {
    LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
        LumeText(text = title, style = LumeTextStyle.HeadingLarge)
        LumeText(text = body, color = LumeTextColor.Secondary)
    }
}

/** 1b-e3's banner, on whichever screen lost the network. What was typed stays. */
@Composable
internal fun OfflineBanner(visible: Boolean) {
    if (visible) {
        LumeCallout(
            title = stringResource(Res.string.offline_title),
            text = stringResource(Res.string.offline_body),
            style = LumeCalloutStyle.Warning,
            icon = LumeIcon.Wifi,
        )
    }
}

/** A refusal the screen explains inline (1b-e1): the bold line and the line under it. */
@Composable
internal fun ErrorBanner(title: String, body: String) {
    LumeCallout(title = title, text = body, style = LumeCalloutStyle.Error, icon = LumeIcon.Alert)
}

/**
 * A step's primary action: the kit's result button, which spins while the step runs and flashes how it ended. A
 * refusal flashes its text; a failure the person did not cause flashes briefly and the flow's alert says the
 * rest; [retryEpoch] re-runs the step when that alert's retry is chosen.
 */
@Composable
internal fun AccessPrimaryButton(
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
                StepOutcome.Advanced -> LumeResult(LumeResultKind.Success, getString(Res.string.common_done))
                is StepOutcome.Rejected -> LumeResult(LumeResultKind.Error, outcome.text.resolveNow())
                StepOutcome.Blocked -> LumeResult(LumeResultKind.Error, getString(Res.string.common_failed_short))
                is StepOutcome.Retry -> LumeResult(LumeResultKind.Warning, outcome.text.resolveNow())
            }
        },
    )
}

/**
 * Whole seconds until [untilEpochMillis] on [clock], ticking once a second and stopping at zero. `null` in, `null`
 * out. Read from the clock every tick rather than counted down, so a rotation or a slow frame cannot drift it.
 */
@Composable
internal fun secondsUntil(untilEpochMillis: Long?, clock: Clock): Long? {
    if (untilEpochMillis == null) return null
    fun remaining() =
        ((untilEpochMillis - clock.nowEpochMillis()) + MILLIS_PER_SECOND - 1).coerceAtLeast(0) / MILLIS_PER_SECOND
    var seconds by remember(untilEpochMillis) { mutableLongStateOf(remaining()) }
    LaunchedEffect(untilEpochMillis) {
        while (seconds > 0) {
            delay(MILLIS_PER_SECOND)
            seconds = remaining()
        }
    }
    return seconds
}

/** "14:32" — minutes and seconds, the design's countdown format in both languages. */
internal fun clockText(seconds: Long): String {
    val minutes = seconds / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return "$minutes:${rest.toString().padStart(2, '0')}"
}

/** The pair of actions at the foot of an outcome screen: a filled one, and a plain one under it. */
@Composable
internal fun ResultActions(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    LumeStack(spacing = { small }) {
        LumeButton(content = LumeButtonContent.Title(primary), width = LumeButtonWidth.Fill, onClick = onPrimary)
        if (secondary != null) {
            LumeButton(
                content = LumeButtonContent.Title(secondary),
                emphasis = LumeButtonEmphasis.Plain,
                width = LumeButtonWidth.Fill,
                onClick = onSecondary,
            )
        }
    }
}

/** "CS" for Camila Soto: the first letter of the first given name and of the first family name. */
internal fun initialsOf(givenNames: String, familyNames: String): String =
    listOfNotNull(givenNames.trim().firstOrNull(), familyNames.trim().firstOrNull()).joinToString("").uppercase()
