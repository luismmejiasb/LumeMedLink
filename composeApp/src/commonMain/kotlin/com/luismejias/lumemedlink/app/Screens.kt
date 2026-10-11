package com.luismejias.lumemedlink.app

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.features.auth.flow.AccessRole
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.home_sign_out
import com.luismejias.lumemedlink.resources.home_title
import com.luismejias.lumemedlink.resources.pending_body
import com.luismejias.lumemedlink.resources.pending_doctor_title
import com.luismejias.lumemedlink.resources.pending_patient_title
import com.luismejias.lumemedlink.resources.pending_restart
import org.jetbrains.compose.resources.stringResource

// The home placeholder, dressed by the design kit (S0.3, ADR-0033); it waits for the main app.

@Composable
internal fun HomeScreen(onSignOut: () -> Unit) {
    LumeContainer(alignment = LumeContainerAlignment.Center) {
        LumeEmptyState(
            icon = LumeIcon.Home,
            title = stringResource(Res.string.home_title),
            action = LumeButtonContent.Title(stringResource(Res.string.home_sign_out)),
            onAction = onSignOut,
        )
    }
}

/**
 * Where the access flow ends while it begins no session (ADR-0044): the role that came in, and a way back to the
 * start. Replaced by each role's main app as it is built.
 */
@Composable
internal fun PendingMainScreen(role: AccessRole, onRestart: () -> Unit) {
    LumeContainer(alignment = LumeContainerAlignment.Center) {
        LumeEmptyState(
            icon = when (role) {
                AccessRole.PATIENT -> LumeIcon.Home
                AccessRole.DOCTOR -> LumeIcon.Stethoscope
            },
            title = stringResource(
                when (role) {
                    AccessRole.PATIENT -> Res.string.pending_patient_title
                    AccessRole.DOCTOR -> Res.string.pending_doctor_title
                },
            ),
            message = stringResource(Res.string.pending_body),
            action = LumeButtonContent.Title(stringResource(Res.string.pending_restart)),
            onAction = onRestart,
        )
    }
}
