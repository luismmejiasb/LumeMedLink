package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.DoctorCareGateway
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.DEV_DOCTOR_LINK
import com.luismejias.lumemedlink.core.networking.DevTokenProvider
import com.luismejias.lumemedlink.core.networking.DiscardingNetworkLogSink
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.TransportPolicy
import com.luismejias.lumemedlink.core.networking.localBackendHttpEngine
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.core.security.SecurityEventEmitter
import io.ktor.client.engine.HttpClientEngine

/**
 * The doctor's side as the platform on the developer's computer serves it (ADR-0062, ADR-0061's amendment of
 * 2026-10-15): the dev token of the seed's linked clinician, `dev-doctor-link`, and no `X-Lume-Side` — a linked account
 * opens its clinician side by default (the platform's ADR-0101). Only a debug build's environment builds one.
 */
internal class LocalDoctorBackend(
    baseUrl: String,
    securityEvents: SecurityEventEmitter,
    logSink: NetworkLogSink = DiscardingNetworkLogSink,
    engine: HttpClientEngine = localBackendHttpEngine(),
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
        subject = DEV_DOCTOR_LINK,
    )
    private val contract = ContractClient(
        lumeHttpClient(baseUrl, engine, logSink, tokens, securityEvents, TransportPolicy.LocalCleartext),
    )

    val care: DoctorCareGateway = RemoteDoctorCare(contract, SpecialtyNames(contract))
}
