package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.ClockTime
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber
import kotlinx.coroutines.delay

/**
 * The doctor's side against a SCRIPT of the design (m1–m3, ADR-0046): Dra. Mariana Rendón's Wednesday
 * 16 September 2026, with synthetic patients. [failure], when set, answers every read with it.
 */
internal class ScriptedDoctorCare(
    private val failure: CareFailure? = null,
    private val latency: suspend () -> Unit = { delay(LATENCY_MILLIS) },
) : DoctorCareGateway {
    /** What this run confirmed or rejected, by entry id: memory only. */
    private val answered = mutableMapOf<String, AppointmentStatus>()

    private var sharing = true

    override fun today(): CalendarDate = TODAY

    override suspend fun profile(): CareResult<DoctorProfile> = answer(
        DoctorProfile(
            doctor = RENDON,
            linkedToLumeMed = true,
            address = "Av. Providencia 1208, of. 402",
            phone = "+56 2 2345 6789",
            sharesContact = sharing,
            rating = RATING,
            ratingCount = RATING_COUNT,
            pendingCorrections = 2,
        ),
    )

    override suspend fun agenda(day: CalendarDate): CareResult<List<AgendaEntry>> = answer(
        (AGENDA + REQUESTS.filter { answered[it.id] == AppointmentStatus.CONFIRMED })
            .filter { it.date == day && answered[it.id] != AppointmentStatus.CANCELLED }
            .map { entry -> answered[entry.id]?.let { entry.copy(status = it) } ?: entry }
            .sortedBy { it.start },
    )

    override suspend fun requests(): CareResult<List<AgendaEntry>> =
        answer(REQUESTS.filter { it.id !in answered }.sortedWith(compareBy({ it.date }, { it.start })))

    override suspend fun confirm(entry: AgendaEntry): ChangeResult = write(entry, AppointmentStatus.CONFIRMED)

    override suspend fun reject(entry: AgendaEntry): ChangeResult = write(entry, AppointmentStatus.CANCELLED)

    override suspend fun patients(): CareResult<PatientRoster> = answer(PatientRoster(PATIENTS, ATTENDED))

    override suspend fun shareContact(share: Boolean): CareResult<Unit> {
        latency()
        failure?.let { return CareResult.Failed(it) }
        sharing = share
        return CareResult.Ok(Unit)
    }

    private suspend fun write(entry: AgendaEntry, status: AppointmentStatus): ChangeResult {
        latency()
        failure?.let {
            return ChangeResult.Failed(
                if (it ==
                    CareFailure.Offline
                ) {
                    ChangeFailure.Offline
                } else {
                    ChangeFailure.Unavailable
                },
            )
        }
        answered[entry.id] = status
        return ChangeResult.Done
    }

    private suspend fun <T> answer(value: T): CareResult<T> {
        latency()
        return failure?.let { CareResult.Failed(it) } ?: CareResult.Ok(value)
    }

    private companion object {
        const val LATENCY_MILLIS = 400L
        const val RATING = 4.9
        const val RATING_COUNT = 128
        const val ATTENDED = 142
        const val VISIT = 30

        val TODAY = CalendarDate(2026, 9, 16)
        val RENDON = CareDoctor("p-rendon", "Dra.", "Mariana", "Rendón", "Medicina Familiar")

        /** Each patient's visits and how many days ago the last one was. */
        val HISTORY = mapOf(
            "c-ana" to (4 to 21),
            "c-antonio" to (1 to 63),
            "c-camila" to (7 to 9),
            "c-carla" to (2 to 140),
            "c-diego" to (3 to 35),
            "c-pedro" to (5 to 17),
            "c-rosa" to (1 to 280),
            "c-victor" to (2 to 52),
            "c-lucia" to (6 to 12),
            "c-tomas" to (1 to 410),
        )

        fun patient(id: String, given: String, family: String, rut: String, mobile: String?, email: String?) =
            PatientContact(
                id = id,
                givenNames = given,
                familyNames = family,
                rut = ChileanRut.parse(rut),
                mobile = mobile?.let(PhoneNumber::chileanMobile),
                email = email?.let(EmailAddress::parse),
                visits = HISTORY.getValue(id).first,
                lastVisit = TODAY.plusDays(-HISTORY.getValue(id).second),
            )

        val ANA = patient("c-ana", "Ana", "Rivas", "12.345.678-5", "+56977123345", "ana.rivas@correo.example")
        val ANTONIO = patient("c-antonio", "Antonio", "Vidal", "15.234.876-2", "+56966219087", "a.vidal@correo.example")
        val CAMILA = patient("c-camila", "Camila", "Soto", "18.456.789-K", "+56987654321", "camila.soto@correo.example")
        val CARLA = patient("c-carla", "Carla", "Núñez", "16.789.432-1", "+56955432210", null)
        val DIEGO = patient("c-diego", "Diego", "Lagos", "17.654.321-3", "+56944321987", "dlagos@correo.example")
        val PEDRO = patient("c-pedro", "Pedro", "Soto", "14.567.890-0", "+56933210876", "pedro.soto@correo.example")
        val ROSA = patient("c-rosa", "Rosa", "Muñoz", "19.876.543-0", "+56922109765", "rmunoz@correo.example")
        val VICTOR = patient("c-victor", "Víctor", "Pinto", "13.579.246-2", "+56911098654", null)
        val LUCIA = patient("c-lucia", "Lucía", "Fernández", "11.223.344-K", "+56990987543", "lucia.f@correo.example")
        val TOMAS = patient("c-tomas", "Tomás", "Herrera", "20.123.456-5", "+56989876432", null)

        val PATIENTS = listOf(ANA, ANTONIO, CAMILA, CARLA, DIEGO, LUCIA, PEDRO, ROSA, TOMAS, VICTOR)

        fun at(
            id: String,
            who: PatientContact,
            day: Int,
            hour: Int,
            minute: Int,
            kind: VisitKind,
            status: AppointmentStatus,
        ) = AgendaEntry(id, who, TODAY.plusDays(day), ClockTime(hour, minute), VISIT, kind, status)

        /** The design's «Hoy · 7 citas», and a few on the other days of the week. */
        val AGENDA = listOf(
            at("e-0900", CAMILA, 0, 9, 0, VisitKind.FOLLOW_UP, AppointmentStatus.CONFIRMED),
            at("e-0930", PEDRO, 0, 9, 30, VisitKind.TELECONSULT, AppointmentStatus.CONFIRMED),
            at("e-1030", ROSA, 0, 10, 30, VisitKind.FOLLOW_UP, AppointmentStatus.CONFIRMED),
            at("e-1100", ANA, 0, 11, 0, VisitKind.FIRST_VISIT, AppointmentStatus.REQUESTED),
            at("e-1500", ANTONIO, 0, 15, 0, VisitKind.FOLLOW_UP, AppointmentStatus.CONFIRMED),
            at("e-1530", CARLA, 0, 15, 30, VisitKind.TELECONSULT, AppointmentStatus.CONFIRMED),
            at("e-1600", DIEGO, 0, 16, 0, VisitKind.FOLLOW_UP, AppointmentStatus.CONFIRMED),
            at("e-mon-1", LUCIA, -2, 10, 0, VisitKind.FOLLOW_UP, AppointmentStatus.COMPLETED),
            at("e-tue-1", TOMAS, -1, 11, 30, VisitKind.FIRST_VISIT, AppointmentStatus.COMPLETED),
            at("e-mon-21", CAMILA, 5, 15, 0, VisitKind.FOLLOW_UP, AppointmentStatus.CONFIRMED),
        )

        /** m1's «Solicitudes por confirmar · 3». */
        val REQUESTS = listOf(
            at("r-victor", VICTOR, 5, 9, 0, VisitKind.FIRST_VISIT, AppointmentStatus.REQUESTED),
            at("r-lucia", LUCIA, 6, 10, 0, VisitKind.FOLLOW_UP, AppointmentStatus.REQUESTED),
            at("r-tomas", TOMAS, 7, 15, 30, VisitKind.TELECONSULT, AppointmentStatus.REQUESTED),
        )
    }
}
