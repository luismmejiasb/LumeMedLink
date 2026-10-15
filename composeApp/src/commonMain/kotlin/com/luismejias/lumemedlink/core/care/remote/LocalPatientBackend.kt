package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AppointmentChanges
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.care.ContactChangeGateway
import com.luismejias.lumemedlink.core.care.DirectoryGateway
import com.luismejias.lumemedlink.core.care.MyDoctorsSource
import com.luismejias.lumemedlink.core.care.NoticeMark
import com.luismejias.lumemedlink.core.care.NoticesSource
import com.luismejias.lumemedlink.core.care.PatientProfileSource
import com.luismejias.lumemedlink.core.care.RatingsGateway
import com.luismejias.lumemedlink.core.networking.AccountSide
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.DevTokenProvider
import com.luismejias.lumemedlink.core.networking.DiscardingNetworkLogSink
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.TransportPolicy
import com.luismejias.lumemedlink.core.networking.localBackendHttpEngine
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.core.security.SecurityEventEmitter
import io.ktor.client.engine.HttpClientEngine

/**
 * The patient's side as the platform on the developer's computer serves it (ADR-0062): plain http to a local host, the
 * dev token for `dev-patient`, synthetic data from the platform's seed. Only a debug build's environment builds one.
 *
 * It serves each capability the contract has; the properties below are the list (tasks `0036` and `0037`). The rest
 * of the patient's side stays on the script until its capability is wired, so the composition root composes the two.
 */
internal class LocalPatientBackend(
    baseUrl: String,
    securityEvents: SecurityEventEmitter,
    /** The bell's read mark, kept on the phone because the platform keeps none. */
    noticeMark: NoticeMark,
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
    private val specialties = SpecialtyNames(contract)

    // One map of each place's zone for both: an hour shown from the directory is moved or booked on the same clock.
    private val zones = PlaceZones()
    private val remoteAppointments = RemoteAppointments(contract, specialties, zones)

    val clock: ClinicClock = ClinicWallClock()
    val profile: PatientProfileSource = RemoteProfile(contract)
    val contact: ContactChangeGateway = RemoteContactChange(contract, tokens::renew)
    val appointments: AppointmentsSource = remoteAppointments
    val changes: AppointmentChanges = remoteAppointments
    val directory: DirectoryGateway = RemoteDirectory(contract, specialties, zones)
    val myDoctors: MyDoctorsSource = RemoteMyDoctors(contract, specialties, remoteAppointments)
    val notices: NoticesSource = RemoteNotices(contract, noticeMark)
    val ratings: RatingsGateway = RemoteRatings(contract, remoteAppointments, zones)
}
