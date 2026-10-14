package com.luismejias.lumemedlink.features.auth.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeAlert
import cl.lume.uicomposer.components.LumeAlertAction
import cl.lume.uicomposer.components.LumeNavigationStack
import cl.lume.uicomposer.tokens.LumeStatusStyle
import com.luismejias.lumemedlink.core.device.rememberDeviceSettings
import com.luismejias.lumemedlink.features.auth.recovery.NewPasswordScreen
import com.luismejias.lumemedlink.features.auth.recovery.RecoveryCodeScreen
import com.luismejias.lumemedlink.features.auth.recovery.RecoveryDoneScreen
import com.luismejias.lumemedlink.features.auth.recovery.RecoveryRutScreen
import com.luismejias.lumemedlink.features.auth.signin.LockedOutScreen
import com.luismejias.lumemedlink.features.auth.signin.QuickSignInScreen
import com.luismejias.lumemedlink.features.auth.signin.QuickSignInUnavailableScreen
import com.luismejias.lumemedlink.features.auth.signin.SignInScreen
import com.luismejias.lumemedlink.features.auth.signup.SignUpCodeExpiredScreen
import com.luismejias.lumemedlink.features.auth.signup.SignUpCodeScreen
import com.luismejias.lumemedlink.features.auth.signup.SignUpCredentialsScreen
import com.luismejias.lumemedlink.features.auth.signup.SignUpDoneScreen
import com.luismejias.lumemedlink.features.auth.signup.SignUpProfileScreen
import com.luismejias.lumemedlink.features.auth.welcome.WelcomeScreen
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_close
import com.luismejias.lumemedlink.resources.common_retry
import com.luismejias.lumemedlink.resources.unavailable_body
import com.luismejias.lumemedlink.resources.unavailable_title
import org.jetbrains.compose.resources.stringResource

/**
 * The access flow, full screen as the design draws it: the top route, forward moves entering from the trailing
 * side and back moves from the leading one, a back disc only where the route allows it, and one alert for a
 * server that could not answer. Screens are dumb; every navigation decision is the coordinator's or a model's.
 */
@Composable
internal fun AccessHost(
    viewModel: AccessFlowViewModel,
    /** When the flow was opened from the lock screen: back on the first screen returns there. */
    onCancel: (() -> Unit)? = null,
) {
    val attempt by viewModel.attempt.collectAsState()
    val flow = attempt.flow
    val routes by flow.routes.collectAsState()
    val unavailable by flow.unavailable.collectAsState()
    val retryEpoch by flow.retryEpoch.collectAsState()
    val account by attempt.createdAccount.collectAsState()
    val settings = rememberDeviceSettings()
    val clock = viewModel.clock

    // The kit's stack draws push and pop as iOS does (kit 227a717). Its edge drag on iOS goes back only where the
    // route allows it, as the back disc does.
    LumeNavigationStack(
        stack = routes,
        onBack = {
            when {
                routes.last().allowsBack -> flow.back()
                routes.size == 1 && routes.last() == AccessRoute.WELCOME -> onCancel?.invoke()
            }
        },
    ) { route ->
        val back: (() -> Unit)? = when {
            route.allowsBack -> ({ flow.back() })
            route == AccessRoute.WELCOME && onCancel != null -> onCancel
            else -> null
        }
        when (route) {
            AccessRoute.WELCOME -> WelcomeScreen(
                onCreateAccount = { flow.push(AccessRoute.SIGN_UP_PROFILE) },
                onSignIn = { flow.push(AccessRoute.SIGN_IN) },
            )
            AccessRoute.SIGN_IN -> SignInScreen(
                model = attempt.signIn,
                retryEpoch = retryEpoch,
                onBack = back,
                onForgot = { flow.push(AccessRoute.RECOVERY_RUT) },
                onCreateAccount = { flow.replaceWith(AccessRoute.WELCOME, AccessRoute.SIGN_UP_PROFILE) },
            )
            AccessRoute.LOCKED_OUT -> LockedOutScreen(
                model = attempt.signIn,
                clock = clock,
                onRecover = { flow.push(AccessRoute.RECOVERY_RUT) },
                onUnderstood = { flow.backTo(AccessRoute.SIGN_IN) },
            )
            AccessRoute.QUICK_SIGN_IN -> QuickSignInScreen(attempt.quickSignIn, retryEpoch)
            AccessRoute.QUICK_SIGN_IN_UNAVAILABLE -> QuickSignInUnavailableScreen(
                onOpenSettings = settings::openBiometricSettings,
                onRestart = viewModel::restart,
            )
            AccessRoute.SIGN_UP_PROFILE -> SignUpProfileScreen(
                model = attempt.signUpProfile,
                retryEpoch = retryEpoch,
                onBack = back,
                onSignInInstead = { rut ->
                    attempt.signIn.prefill(rut)
                    flow.replaceWith(AccessRoute.WELCOME, AccessRoute.SIGN_IN)
                },
                onRecoverInstead = { flow.push(AccessRoute.RECOVERY_RUT) },
            )
            AccessRoute.SIGN_UP_CREDENTIALS -> SignUpCredentialsScreen(attempt.signUpCredentials, retryEpoch, back)
            AccessRoute.SIGN_UP_CODE -> SignUpCodeScreen(attempt.signUpCode, clock, retryEpoch, back)
            AccessRoute.SIGN_UP_CODE_EXPIRED -> SignUpCodeExpiredScreen(attempt.signUpCode, retryEpoch, back)
            AccessRoute.SIGN_UP_DONE -> SignUpDoneScreen(
                account = account,
                onContinue = { flow.replaceWith(AccessRoute.QUICK_SIGN_IN) },
            )
            AccessRoute.RECOVERY_RUT -> RecoveryRutScreen(attempt.recoveryRut, retryEpoch, back)
            AccessRoute.RECOVERY_CODE -> RecoveryCodeScreen(attempt.recoveryCode, clock, retryEpoch, back)
            AccessRoute.RECOVERY_NEW_PASSWORD -> NewPasswordScreen(attempt.newPassword, retryEpoch, back)
            AccessRoute.RECOVERY_DONE -> RecoveryDoneScreen(
                onSignIn = { flow.replaceWith(AccessRoute.WELCOME, AccessRoute.SIGN_IN) },
            )
        }
    }

    LumeAlert(
        visible = unavailable,
        title = stringResource(Res.string.unavailable_title),
        message = stringResource(Res.string.unavailable_body),
        style = LumeStatusStyle.Error,
        confirm = LumeAlertAction(title = stringResource(Res.string.common_retry), onSelect = flow::alertRetried),
        cancel = LumeAlertAction(title = stringResource(Res.string.common_close), onSelect = flow::alertDismissed),
    )
}
