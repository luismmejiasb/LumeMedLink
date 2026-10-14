package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AppointmentChanges
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.networking.AccountSide
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.DevTokenProvider
import com.luismejias.lumemedlink.core.networking.DiscardingNetworkLogSink
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.TransportPolicy
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.core.networking.platformHttpEngine
import com.luismejias.lumemedlink.core.security.SecurityEventEmitter
import io.ktor.client.engine.HttpClientEngine

/**
 * The patient's side as the platform on the developer's computer serves it (ADR-0062): plain http to a local host, the
 * dev token for `dev-patient`, synthetic data from the platform's seed. Only a debug build's environment builds one.
 *
 * What it serves today is what the contract has (task `0036`): the clinic's clock and the appointments. The rest of the
 * patient's side stays on the script until its capability is wired, so the composition root composes the two.
 */
internal class LocalPatientBackend(
    baseUrl: String,
    securityEvents: SecurityEventEmitter,
    logSink: NetworkLogSink = DiscardingNetworkLogSink,
    engine: HttpClientEngine = platformHttpEngine(),
) {
    // The token's own stack has no token provider: it IS how the token is fetched (DevTokenProvider's KDoc).
    private val tokens = DevTokenProvider(
        ContractClient(
            lumeHttpClient(
                baseUrl,
                engine,
                logSink,
                securityEvents = securityEvents,
                transport = TransportPolicy.LocalCleartext,
            ),
        ),
    )
    private val contract = ContractClient(
        lumeHttpClient(
            baseUrl,
            engine,
            logSink,
            tokens,
            securityEvents,
            TransportPolicy.LocalCleartext,
            // A linked clinician who is also a patient opens the doctor's side without it (the platform's 0075).
            AccountSide.PATIENT,
        ),
    )
    private val remoteAppointments = RemoteAppointments(contract, SpecialtyNames(contract))

    val clock: ClinicClock = ClinicWallClock()
    val appointments: AppointmentsSource = remoteAppointments
    val changes: AppointmentChanges = remoteAppointments
}
