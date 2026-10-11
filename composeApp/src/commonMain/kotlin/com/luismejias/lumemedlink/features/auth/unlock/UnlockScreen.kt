package com.luismejias.lumemedlink.features.auth.unlock

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardInset
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeIconTile
import cl.lume.uicomposer.components.LumeIconTileSize
import cl.lume.uicomposer.components.LumeLink
import cl.lume.uicomposer.components.LumeLinkSize
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextAlignment
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.session.LockOutcome
import com.luismejias.lumemedlink.features.auth.flow.AccessPrimaryButton
import com.luismejias.lumemedlink.features.auth.flow.AccessText
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.unlock_action
import com.luismejias.lumemedlink.resources.unlock_attempts_left
import com.luismejias.lumemedlink.resources.unlock_body
import com.luismejias.lumemedlink.resources.unlock_change_account
import com.luismejias.lumemedlink.resources.unlock_not_you
import com.luismejias.lumemedlink.resources.unlock_title
import com.luismejias.lumemedlink.resources.unlock_try_again
import com.luismejias.lumemedlink.resources.unlock_use_password
import org.jetbrains.compose.resources.stringResource

/**
 * What a lock outcome means on this screen. Pulled out so a test can reach it: the attempts left are the
 * one thing the person needs to see, and a dismissed prompt must not read as a failure.
 */
internal fun stepOutcomeFor(outcome: LockOutcome): StepOutcome = when (outcome) {
    LockOutcome.Unlocked -> StepOutcome.Advanced
    is LockOutcome.StillLocked -> when (val left = outcome.remainingAttempts) {
        null -> StepOutcome.Retry(AccessText.Plain(Res.string.unlock_try_again))
        else -> StepOutcome.Rejected(AccessText.Counted(Res.plurals.unlock_attempts_left, left))
    }
    // The shell ends the session; the button only needs to stop.
    is LockOutcome.SessionEnded -> StepOutcome.Blocked
}

/**
 * LumeMed's `FaceIDLoginFormView`, as this app's lock screen: "Hola de nuevo", the biometric glyph, the one
 * filled action, "Usar contraseña", and "¿No eres tú?" with "Cambiar de cuenta". No name in the greeting:
 * LumeMed shows one, but this app would have to keep the person's name on the device to show it, and the
 * name on a locked screen is exactly what the shared-phone threat reads (§8.17).
 *
 * "Usar contraseña" signs in again from the start — a new session replaces this one (ADR-0037).
 * "Cambiar de cuenta" ends this session with the full erase first.
 */
@Composable
internal fun UnlockScreen(
    retryEpoch: Int,
    onUnlock: suspend () -> LockOutcome,
    onUnlocked: () -> Unit,
    onSessionEnded: () -> Unit,
    onUsePassword: () -> Unit,
    onChangeAccount: () -> Unit,
) {
    LumeContainer(alignment = LumeContainerAlignment.Center) {
        LumeCard(inset = LumeCardInset.Focal, alignment = LumeStackAlignment.Center) {
            LumeStack(spacing = { large }) {
                LumeStack(spacing = { small }) {
                    LumeText(
                        text = stringResource(Res.string.unlock_title),
                        style = LumeTextStyle.HeadingLarge,
                        alignment = LumeTextAlignment.Center,
                    )
                    LumeText(
                        text = stringResource(Res.string.unlock_body),
                        color = LumeTextColor.Secondary,
                        alignment = LumeTextAlignment.Center,
                    )
                }
                LumeIconTile(icon = LumeIcon.Shield, size = LumeIconTileSize.Large)
                AccessPrimaryButton(
                    title = stringResource(Res.string.unlock_action),
                    loadingMessage = stringResource(Res.string.unlock_action),
                    enabled = true,
                    retryEpoch = retryEpoch,
                    run = {
                        val outcome = onUnlock()
                        when (outcome) {
                            LockOutcome.Unlocked -> onUnlocked()
                            is LockOutcome.SessionEnded -> onSessionEnded()
                            is LockOutcome.StillLocked -> Unit
                        }
                        stepOutcomeFor(outcome)
                    },
                )
                LumeLink(title = stringResource(Res.string.unlock_use_password), onClick = onUsePassword)
                LumeStack(spacing = { xSmall }) {
                    LumeText(
                        text = stringResource(Res.string.unlock_not_you),
                        style = LumeTextStyle.Caption,
                        color = LumeTextColor.Secondary,
                    )
                    LumeLink(
                        title = stringResource(Res.string.unlock_change_account),
                        size = LumeLinkSize.Caption,
                        onClick = onChangeAccount,
                    )
                }
            }
        }
    }
}
