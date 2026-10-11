package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ChileanMobile
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.ClockTime
import com.luismejias.lumemedlink.shared.EmailAddress
import kotlinx.coroutines.delay

/** Which of the design's worlds the script plays: its sample patient, or one with nothing yet (e7, e8, e9). */
internal enum class CareWorld { DESIGN, EMPTY }

/**
 * The patient's main app against a SCRIPT of the design (ADR-0046) — synthetic people, the design's own
 * sample, anchored on the day the design draws (Wednesday 16 September 2026) so every "Hoy" lines up.
 * [failure], when set, answers every read with it: the offline and unavailable states, on demand.
 */
internal class ScriptedPatientCare(
    private val world: CareWorld = CareWorld.DESIGN,
    private val failure: CareFailure? = null,
    private val latency: suspend () -> Unit = { delay(LATENCY_MILLIS) },
) : PatientCareGateway {
    override fun today(): CalendarDate = TODAY

    override suspend fun profile(): CareResult<PatientProfile> = answer(PROFILE)

    override suspend fun appointments(): CareResult<List<PatientAppointment>> =
        answer(if (world == CareWorld.DESIGN) APPOINTMENTS else emptyList())

    override suspend fun ratingPrompts(): CareResult<List<RatingPrompt>> = answer(
        if (world == CareWorld.DESIGN) {
            listOf(RatingPrompt("a-fuentes-0902", FUENTES, TODAY.plusDays(-14)))
        } else {
            emptyList()
        },
    )

    override suspend fun myDoctors(): CareResult<List<MyDoctor>> = answer(
        if (world == CareWorld.DESIGN) {
            listOf(
                MyDoctor(RENDON, attentions = 6, phone = "+56 2 2345 6789", email = "consulta@lyon.example"),
                MyDoctor(FUENTES, attentions = 2, phone = "+56 2 2987 6543", email = null),
                MyDoctor(PINTO, attentions = 1, phone = null, email = null),
            )
        } else {
            emptyList()
        },
    )

    override suspend fun recentSearches(): CareResult<List<RecentSearch>> = answer(
        if (world == CareWorld.DESIGN) {
            listOf(RecentSearch("Medicina general", "Providencia"), RecentSearch("Dermatología", "Las Condes"))
        } else {
            emptyList()
        },
    )

    override suspend fun policy(): CareResult<CarePolicy> =
        answer(CarePolicy(changeWindowHours = 24, remindersHoursBefore = listOf(24, 2)))

    override suspend fun sentRatingCount(): CareResult<Int> = answer(if (world == CareWorld.DESIGN) 3 else 0)

    override suspend fun hasUnreadNotices(): CareResult<Boolean> = answer(world == CareWorld.DESIGN)

    private suspend fun <T> answer(value: T): CareResult<T> {
        latency()
        return failure?.let { CareResult.Failed(it) } ?: CareResult.Ok(value)
    }

    private companion object {
        const val LATENCY_MILLIS = 400L

        val TODAY = CalendarDate(2026, 9, 16)

        val RENDON = CareDoctor("p-rendon", "Dra.", "Mariana", "Rendón", "Medicina Familiar")
        val PINTO = CareDoctor("p-pinto", "Dr.", "Víctor", "Pinto", "Dermatología")
        val FUENTES = CareDoctor("p-fuentes", "Dr.", "Jorge", "Fuentes", "Traumatología")

        val LYON = CarePlace("Av. Providencia 1208, oficina 503", "Centro Médico Lyon · Providencia, Santiago")
        val ALCANTARA = CarePlace("Alcántara 200, piso 4", "Clínica Alcántara · Las Condes, Santiago")
        val APOQUINDO = CarePlace("Av. Apoquindo 3600, oficina 1102", "Centro Apoquindo · Las Condes, Santiago")

        val APPOINTMENTS = listOf(
            PatientAppointment(
                "a-fuentes-0902",
                FUENTES,
                TODAY.plusDays(-14),
                ClockTime(16, 0),
                30,
                AppointmentStatus.COMPLETED,
                APOQUINDO,
            ),
            PatientAppointment(
                "a-rendon-0916",
                RENDON,
                TODAY,
                ClockTime(10, 30),
                30,
                AppointmentStatus.CONFIRMED,
                LYON,
            ),
            PatientAppointment(
                "a-pinto-0921",
                PINTO,
                TODAY.plusDays(5),
                ClockTime(9, 0),
                30,
                AppointmentStatus.REQUESTED,
                ALCANTARA,
            ),
        )

        /** The design's "18.456.789-2" fails módulo 11 (ADR-0044, deviation 9); this one is valid. */
        val PROFILE = PatientProfile(
            givenNames = "Camila",
            familyNames = "Soto",
            rut = requireNotNull(ChileanRut.parse("18.456.789-K")),
            mobile = requireNotNull(ChileanMobile.parse("+56987654321")),
            email = requireNotNull(EmailAddress.parse("camila.soto@correo.cl")),
            verified = true,
        )
    }
}
