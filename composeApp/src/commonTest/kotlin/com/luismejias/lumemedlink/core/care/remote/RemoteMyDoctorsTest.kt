package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CarePlace
import com.luismejias.lumemedlink.core.care.CarePolicy
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.MyDoctor
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NO_SECURITY_EVENTS
import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class Still : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

/** One professional at one clinic, as the contract lists them. Synthetic (§9). */
private fun contactJson(
    clinic: String,
    id: String,
    phone: String?,
    email: String?,
    specialty: String? = "CARDIOLOGIA",
): String {
    fun q(value: String?) = value?.let { "\"$it\"" } ?: "null"
    return """{"tenantId":"$clinic","tenantName":"Clínica $clinic","professionalId":"$id","givenName":"Ana",
        "familyName":"Sintética","secondFamilyName":null,"professionalTitle":"Dra.","phone":${q(phone)},
        "email":${q(email)},"specialtyCode":${q(specialty)}}"""
}

private const val SPECIALTIES = """[{"code":"CARDIOLOGIA","name":"Cardiología","profession":"MEDICINE","nidCode":null,
    "isSuppressed":false}]"""

private class Appointments(private val answer: CareResult<List<PatientAppointment>>) : AppointmentsSource {
    override suspend fun appointments() = answer

    override suspend fun policy(): CareResult<CarePolicy> = CareResult.Ok(CarePolicy(24, listOf(24, 2)))
}

private fun visit(doctor: String, status: AppointmentStatus) = PatientAppointment(
    id = "a-$doctor-$status",
    doctor = CareDoctor(doctor, "Dra. Ana Sintética", ""),
    date = CalendarDate(2026, 9, 1),
    start = ClockTime(10, 0),
    minutes = 30,
    status = status,
    place = CarePlace("", ""),
)

private fun source(contacts: String, appointments: CareResult<List<PatientAppointment>>): RemoteMyDoctors {
    val engine = MockEngine { request ->
        val body = if (request.url.encodedPath.endsWith("specialties")) SPECIALTIES else contacts
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val contract = ContractClient(
        lumeHttpClient("https://api.test.lume", engine, Still(), securityEvents = NO_SECURITY_EVENTS),
    )
    return RemoteMyDoctors(contract, SpecialtyNames(contract), Appointments(appointments))
}

class RemoteMyDoctorsTest {
    @Test
    fun aDoctorSeenAtTwoClinicsIsOneEntryWithTheChannelsEachShares() = runTest {
        val doctors = assertIs<CareResult.Ok<List<MyDoctor>>>(
            source(
                """{"items":[${contactJson("c-1", "pro-1", null, "ana@consulta.test")},""" +
                    """${contactJson("c-2", "pro-1", "+56223456789", null)}]}""",
                CareResult.Ok(emptyList()),
            ).myDoctors(),
        ).value
        val one = doctors.single()
        assertEquals("Dra. Ana Sintética" to "Cardiología", one.doctor.displayName to one.doctor.specialty)
        assertEquals("+56223456789" to "ana@consulta.test", one.phone to one.email)
    }

    @Test
    fun theAttentionsAreTheCompletedAppointmentsWithThatDoctor() = runTest {
        val doctors = assertIs<CareResult.Ok<List<MyDoctor>>>(
            source(
                """{"items":[${contactJson("c-1", "pro-1", null, null)},${contactJson("c-1", "pro-2", null, null)}]}""",
                CareResult.Ok(
                    listOf(
                        visit("pro-1", AppointmentStatus.COMPLETED),
                        visit("pro-1", AppointmentStatus.COMPLETED),
                        visit("pro-1", AppointmentStatus.CANCELLED),
                        visit("pro-2", AppointmentStatus.CONFIRMED),
                    ),
                ),
            ).myDoctors(),
        ).value
        assertEquals(mapOf("pro-1" to 2, "pro-2" to 0), doctors.associate { it.doctor.id to it.attentions })
        assertEquals(false, doctors.first { it.doctor.id == "pro-2" }.sharesContact, "both channels null")
    }

    @Test
    fun appointmentsThatCannotBeReadFailTheScreenRatherThanCountZero() = runTest {
        assertEquals(
            CareResult.Failed(CareFailure.Offline),
            source("""{"items":[]}""", CareResult.Failed(CareFailure.Offline)).myDoctors(),
        )
    }
}
