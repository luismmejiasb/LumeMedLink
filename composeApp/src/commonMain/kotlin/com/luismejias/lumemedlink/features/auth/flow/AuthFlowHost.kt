package com.luismejias.lumemedlink.features.auth.flow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import cl.lume.uicomposer.components.LumeAlert
import cl.lume.uicomposer.components.LumeAlertAction
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardInset
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeGlassButton
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.foundations.LumeTheme
import cl.lume.uicomposer.foundations.lumeAnimationsEnabled
import cl.lume.uicomposer.tokens.LumeStatusStyle
import com.luismejias.lumemedlink.core.session.SessionEntryOutcome
import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.features.auth.biometric.BiometricEnrollmentScreen
import com.luismejias.lumemedlink.features.auth.login.LoginScreen
import com.luismejias.lumemedlink.features.auth.mfa.CodeStepScreen
import com.luismejias.lumemedlink.features.auth.recovery.NewPasswordScreen
import com.luismejias.lumemedlink.features.auth.recovery.PasswordRecoveryScreen
import com.luismejias.lumemedlink.features.auth.totp.TotpEnrollmentScreen

/** Each move slides a third of the card's width: the drift is a hint of direction, not a page turn. */
private const val DRIFT_DIVISOR = 3

/**
 * The sign-in flow as LumeMed draws it (its ADR-0022): one focal card, the top route rendered inside it,
 * forward moves entering from the trailing side and back moves from the leading one, a back disc on the
 * card's corner only where the route allows it, and one alert for the whole flow.
 */
@Composable
internal fun AuthFlowHost(
    session: AuthFlowSession,
    establish: suspend (SessionTokens) -> SessionEntryOutcome,
    onEstablished: () -> Unit,
    /** When the flow was opened from the lock screen: a back disc on the sign-in returns there. */
    onCancel: (() -> Unit)? = null,
) {
    val flow = session.flow
    val routes by flow.routes.collectAsState()
    val move by flow.lastMove.collectAsState()
    val alert by flow.alert.collectAsState()
    val retryEpoch by flow.retryEpoch.collectAsState()
    val top = routes.last()
    val duration = LumeTheme.current.motion.appear
    val animates = lumeAnimationsEnabled()

    LumeContainer(alignment = LumeContainerAlignment.Center, scroll = LumeContainerScroll.Scrollable) {
        Box {
            LumeCard(inset = LumeCardInset.Focal, alignment = LumeStackAlignment.Center) {
                AnimatedContent(
                    targetState = top,
                    transitionSpec = {
                        val sign = if (move == AuthMove.FORWARD) 1 else -1
                        if (!animates) {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        } else {
                            (
                                fadeIn(tween(duration)) +
                                    slideInHorizontally(tween<IntOffset>(duration)) { sign * it / DRIFT_DIVISOR }
                                ) togetherWith (
                                fadeOut(tween(duration)) +
                                    slideOutHorizontally(tween<IntOffset>(duration)) { -sign * it / DRIFT_DIVISOR }
                                )
                        }
                    },
                    label = "auth-route",
                ) { route ->
                    when (route) {
                        AuthRoute.LOGIN -> LoginScreen(session.login, flow, retryEpoch)
                        AuthRoute.MFA_CHALLENGE -> CodeStepScreen(
                            model = session.mfa,
                            title = AuthCopy.MFA_TITLE,
                            body = AuthCopy.MFA_BODY,
                            action = AuthCopy.VERIFY,
                            running = AuthCopy.VERIFYING,
                            icon = LumeIcon.Shield,
                            retryEpoch = retryEpoch,
                        )
                        AuthRoute.TOTP_ENROLLMENT -> TotpEnrollmentScreen(session.totpEnrollment, retryEpoch)
                        AuthRoute.TOTP_ENROLLMENT_CONFIRM -> CodeStepScreen(
                            model = session.totpConfirm,
                            title = AuthCopy.TOTP_CONFIRM_TITLE,
                            body = AuthCopy.TOTP_CONFIRM_BODY,
                            action = AuthCopy.CONFIRM,
                            running = AuthCopy.CONFIRMING,
                            icon = LumeIcon.Shield,
                            retryEpoch = retryEpoch,
                        )
                        AuthRoute.BIOMETRIC_ENROLLMENT ->
                            BiometricEnrollmentScreen(session.biometric, establish, onEstablished, retryEpoch)
                        AuthRoute.PASSWORD_RECOVERY -> PasswordRecoveryScreen(session.recovery, retryEpoch)
                        AuthRoute.PASSWORD_RESET_CODE -> CodeStepScreen(
                            model = session.resetCode,
                            title = AuthCopy.RESET_CODE_TITLE,
                            body = AuthCopy.RESET_CODE_BODY,
                            action = AuthCopy.CONTINUE,
                            running = AuthCopy.VERIFYING,
                            icon = LumeIcon.Mail,
                            retryEpoch = retryEpoch,
                        )
                        AuthRoute.PASSWORD_RESET_NEW_PASSWORD -> NewPasswordScreen(session.newPassword, retryEpoch)
                    }
                }
            }
            val cancel = onCancel.takeIf { routes.size == 1 }
            if (top.allowsBack || cancel != null) {
                LumeGlassButton(
                    icon = LumeIcon.ChevronLeft,
                    contentDescription = AuthCopy.BACK,
                    modifier = Modifier.align(Alignment.TopStart),
                    onClick = { if (!flow.back()) cancel?.invoke() },
                )
            }
        }
    }

    val shown = alert
    LumeAlert(
        visible = shown != null,
        title = shown?.title.orEmpty(),
        message = shown?.message,
        style = if (shown?.retryable == true) LumeStatusStyle.Error else LumeStatusStyle.Warning,
        confirm = LumeAlertAction(
            title = if (shown?.retryable == true) AuthCopy.RETRY else AuthCopy.CLOSE,
            onSelect = { flow.alertConfirmed() },
        ),
        cancel = if (shown?.retryable == true) {
            LumeAlertAction(title = AuthCopy.CLOSE, onSelect = { flow.alertDismissed() })
        } else {
            null
        },
    )
}
