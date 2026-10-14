package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.core.auth.AccessScript
import com.luismejias.lumemedlink.core.session.Clock
import com.luismejias.lumemedlink.core.session.SystemClock
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.ClockTime
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber
import kotlinx.coroutines.delay

/** The script's one pending contact change names itself: it keeps the change, the handle only points at it. */
private const val SCRIPT_CHANGE_ID = "script"

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
    /** What a contact change's code expiry is measured on (§6). */
    private val clock: Clock = SystemClock,
) : PatientCareGateway,
    DirectoryGateway {
    /** What this run booked, by its client key: memory only, gone with the instance. */
    private val booked = LinkedHashMap<String, PatientAppointment>()

    /** What this run moved, by the move's client key: the new appointment, waiting for the doctor (spec f3). */
    private val moved = LinkedHashMap<String, PatientAppointment>()

    /** What this run rated, by appointment id. */
    private val rated = mutableMapOf<String, SentRating>()

    /** What this run cancelled or withdrew, by appointment id. */
    private val cancelled = mutableSetOf<String>()

    /** The profile as this run left it: a confirmed contact change replaces its number or its address. */
    private var profile = PROFILE

    /** The contact change waiting for its code, as a server would keep it. */
    private var pendingContact: PendingContact? = null

    private class PendingContact(
        val mobile: PhoneNumber?,
        val email: EmailAddress?,
        var expiresAt: Long,
        var attemptsLeft: Int,
    )

    override fun today(): CalendarDate = TODAY

    override suspend fun ratingFor(appointmentId: String): CareResult<SentRating?> =
        answer(rated[appointmentId] ?: if (world == CareWorld.DESIGN) RATINGS[appointmentId] else null)

    override suspend fun rate(appointmentId: String, draft: RatingDraft): CareResult<Unit> {
        latency()
        failure?.let { return CareResult.Failed(it) }
        rated[appointmentId] = SentRating(draft.stars, draft.comment, TODAY)
        return CareResult.Ok(Unit)
    }

    override suspend fun doctor(doctorId: String): CareResult<DirectoryDoctor?> =
        answer(DirectoryScript.doctor(doctorId, TODAY, NOW, ::isBooked))

    /** The design's status bar: 9:41. */
    override fun now(): ClockTime = NOW

    override suspend fun profile(): CareResult<PatientProfile> = answer(profile)

    override suspend fun appointments(): CareResult<List<PatientAppointment>> = answer(all())

    private fun all(): List<PatientAppointment> =
        ((if (world == CareWorld.DESIGN) APPOINTMENTS else emptyList()) + booked.values + moved.values).map {
            if (it.id in cancelled) it.copy(status = AppointmentStatus.CANCELLED) else it
        }

    override suspend fun ratingPrompts(): CareResult<List<RatingPrompt>> = answer(
        if (world == CareWorld.DESIGN) {
            listOf(RatingPrompt("a-fuentes-0902", FUENTES, TODAY.plusDays(-14))).filterNot { it.appointmentId in rated }
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

    override suspend fun policy(): CareResult<CarePolicy> =
        answer(CarePolicy(changeWindowHours = 24, remindersHoursBefore = listOf(24, 2)))

    override suspend fun sentRatingCount(): CareResult<Int> = answer(
        (if (world == CareWorld.DESIGN) 3 else 0) + rated.size,
    )

    override suspend fun hasUnreadNotices(): CareResult<Boolean> = answer(world == CareWorld.DESIGN)

    override suspend fun specialties(): CareResult<List<Specialty>> = answer(DirectoryScript.SPECIALTIES)

    override suspend fun communes(specialtyCode: String): CareResult<List<Commune>> =
        answer(DirectoryScript.communesWith(specialtyCode))

    override suspend fun search(specialtyCode: String, communeCode: String): CareResult<List<DirectoryDoctor>> {
        if (communeCode == DirectoryScript.OFFLINE_COMMUNE) return CareResult.Failed(CareFailure.Offline)
        return answer(DirectoryScript.doctorsIn(specialtyCode, communeCode, TODAY, NOW, ::isBooked))
    }

    override suspend fun nearbyCommunes(specialtyCode: String, communeCode: String): CareResult<List<NearbyCommune>> =
        answer(DirectoryScript.nearby(specialtyCode, communeCode, TODAY))

    override suspend fun availability(
        doctorId: String,
        locationId: String,
        from: CalendarDate,
        to: CalendarDate,
    ): CareResult<List<Slot>> =
        answer(DirectoryScript.slots(doctorId, from, to, TODAY, NOW).filterNot { isBooked(doctorId, it) })

    override suspend fun closedDays(
        doctorId: String,
        locationId: String,
        from: CalendarDate,
        to: CalendarDate,
    ): CareResult<List<ClosedDay>> = answer(DirectoryScript.closedDays(from, to))

    override suspend fun book(doctorId: String, locationId: String, slot: Slot, clientId: String): BookingResult {
        latency()
        failure?.let {
            return BookingResult.Failed(
                if (it ==
                    CareFailure.Offline
                ) {
                    BookingFailure.Offline
                } else {
                    BookingFailure.Unavailable
                },
            )
        }
        booked[clientId]?.let { return BookingResult.Booked(it) }
        val pending = (APPOINTMENTS + booked.values)
            .any { it.doctor.id == doctorId && it.status == AppointmentStatus.REQUESTED && it.date >= TODAY }
        return when {
            pending -> BookingResult.Failed(BookingFailure.AlreadyPending)
            DirectoryScript.takenOnArrival(doctorId, slot, TODAY) || isBooked(doctorId, slot) ->
                BookingResult.Failed(BookingFailure.SlotTaken)
            else -> {
                val appointment = PatientAppointment(
                    id = "a-$clientId",
                    doctor = DirectoryScript.doctorOf(doctorId),
                    date = slot.date,
                    start = slot.start,
                    minutes = slot.minutes,
                    status = AppointmentStatus.REQUESTED,
                    place = DirectoryScript.placeOf(doctorId),
                    locationId = locationId,
                    changeCutoffMinutes = DirectoryScript.changeCutoffMinutes(doctorId),
                )
                booked[clientId] = appointment
                BookingResult.Booked(appointment)
            }
        }
    }

    override suspend fun reschedule(
        appointment: PatientAppointment,
        slot: Slot,
        reason: ChangeReason,
        clientId: String,
    ): ChangeResult {
        refusal()?.let { return it }
        moved[clientId]?.let { return ChangeResult.Moved(it) }
        return when {
            closedWindow(appointment) -> ChangeResult.Failed(ChangeFailure.WindowClosed)
            DirectoryScript.takenOnArrival(
                appointment.doctor.id,
                slot,
                TODAY,
            ) ||
                isBooked(appointment.doctor.id, slot) ->
                ChangeResult.Failed(ChangeFailure.SlotTaken)
            else -> {
                // The original keeps its hour until the doctor confirms the new one (spec f3).
                val next = appointment.copy(
                    id = "m-$clientId",
                    date = slot.date,
                    start = slot.start,
                    minutes = slot.minutes,
                    status = AppointmentStatus.REQUESTED,
                )
                moved[clientId] = next
                ChangeResult.Moved(next)
            }
        }
    }

    override suspend fun cancel(appointment: PatientAppointment, reason: ChangeReason): ChangeResult {
        refusal()?.let { return it }
        if (closedWindow(appointment)) return ChangeResult.Failed(ChangeFailure.WindowClosed)
        cancelled += appointment.id
        return ChangeResult.Done
    }

    override suspend fun withdraw(appointment: PatientAppointment): ChangeResult {
        refusal()?.let { return it }
        cancelled += appointment.id
        return ChangeResult.Done
    }

    override suspend fun requestPhoneChange(mobile: PhoneNumber): ContactChangeResult<ContactCodeDispatch> =
        contactAnswer { ContactChangeResult.Ok(sendContactCode(PendingContact(mobile, null, 0, 0))) }

    override suspend fun requestEmailChange(email: EmailAddress): ContactChangeResult<ContactCodeDispatch> =
        contactAnswer { ContactChangeResult.Ok(sendContactCode(PendingContact(null, email, 0, 0))) }

    override suspend fun resendContactCode(change: PendingContactChange): ContactChangeResult<ContactCodeDispatch> =
        contactAnswer {
            pendingContact?.let { ContactChangeResult.Ok(sendContactCode(it)) }
                ?: ContactChangeResult.Failed(ContactChangeFailure.CodeExpired)
        }

    override suspend fun confirmContactChange(change: PendingContactChange, code: String): ContactChangeResult<Unit> =
        contactAnswer {
            val pending = pendingContact
            when {
                pending == null || clock.nowEpochMillis() >= pending.expiresAt || pending.attemptsLeft <= 0 ->
                    ContactChangeResult.Failed(ContactChangeFailure.CodeExpired)
                code == AccessScript.SMS_CODE -> {
                    profile = profile.copy(
                        mobile = pending.mobile ?: profile.mobile,
                        email = pending.email ?: profile.email,
                    )
                    pendingContact = null
                    ContactChangeResult.Ok(Unit)
                }
                else -> {
                    pending.attemptsLeft -= 1
                    ContactChangeResult.Failed(
                        if (pending.attemptsLeft > 0) {
                            ContactChangeFailure.WrongCode(pending.attemptsLeft)
                        } else {
                            ContactChangeFailure.CodeExpired
                        },
                    )
                }
            }
        }

    /** Arms [pending] with a fresh code — the access script's lifetime, attempts and resend interval. */
    private fun sendContactCode(pending: PendingContact): ContactCodeDispatch {
        val now = clock.nowEpochMillis()
        pending.expiresAt = now + AccessScript.CODE_LIFETIME_MILLIS
        pending.attemptsLeft = AccessScript.CODE_ATTEMPTS
        pendingContact = pending
        // The script keeps its own pending change; the handle only names it.
        val value = pending.mobile?.e164 ?: pending.email?.value.orEmpty()
        return ContactCodeDispatch(
            pending.expiresAt,
            now + AccessScript.RESEND_INTERVAL_MILLIS,
            PendingContactChange(SCRIPT_CHANGE_ID, SCRIPT_CHANGE_ID, value),
        )
    }

    private suspend fun <T> contactAnswer(answer: () -> ContactChangeResult<T>): ContactChangeResult<T> {
        latency()
        return failure?.let {
            ContactChangeResult.Failed(
                if (it == CareFailure.Offline) ContactChangeFailure.Offline else ContactChangeFailure.Unavailable,
            )
        } ?: answer()
    }

    /** The write's own wait, and the failure switch read as a write's failure. */
    private suspend fun refusal(): ChangeResult? {
        latency()
        return failure?.let {
            ChangeResult.Failed(if (it == CareFailure.Offline) ChangeFailure.Offline else ChangeFailure.Unavailable)
        }
    }

    private fun closedWindow(appointment: PatientAppointment): Boolean =
        TODAY.daysUntil(appointment.date) * PatientAppointment.DAY_MINUTES +
            appointment.start.minuteOfDay - NOW.minuteOfDay < appointment.changeCutoffMinutes

    private fun isBooked(doctorId: String, slot: Slot): Boolean = (booked.values + moved.values)
        .any { it.doctor.id == doctorId && it.date == slot.date && it.start == slot.start && it.id !in cancelled }

    private suspend fun <T> answer(value: T): CareResult<T> {
        latency()
        return failure?.let { CareResult.Failed(it) } ?: CareResult.Ok(value)
    }

    private companion object {
        const val LATENCY_MILLIS = 400L

        val TODAY = CalendarDate(2026, 9, 16)
        val NOW = ClockTime(9, 41)

        val RENDON = CareDoctor("p-rendon", "Dra.", "Mariana", "Rendón", "Medicina Familiar")
        val PINTO = CareDoctor("p-pinto", "Dr.", "Víctor", "Pinto", "Dermatología")
        val FUENTES = CareDoctor("p-fuentes", "Dr.", "Jorge", "Fuentes", "Traumatología")

        val LYON = CarePlace("Av. Providencia 1208, oficina 503", "Centro Médico Lyon · Providencia, Santiago")
        val ALCANTARA = CarePlace("Alcántara 200, piso 4", "Clínica Alcántara · Las Condes, Santiago")
        val APOQUINDO = CarePlace("Av. Apoquindo 3600, oficina 1102", "Centro Apoquindo · Las Condes, Santiago")

        val APPOINTMENTS = listOf(
            // c3: attended and rated.
            PatientAppointment(
                "a-rendon-0902",
                RENDON,
                TODAY.plusDays(-14),
                ClockTime(9, 0),
                30,
                AppointmentStatus.COMPLETED,
                LYON,
                locationId = DirectoryScript.locationOf("p-rendon"),
                changeCutoffMinutes = DirectoryScript.changeCutoffMinutes("p-rendon"),
            ),
            PatientAppointment(
                "a-fuentes-0902",
                FUENTES,
                TODAY.plusDays(-14),
                ClockTime(16, 0),
                30,
                AppointmentStatus.COMPLETED,
                APOQUINDO,
                locationId = "l-p-fuentes",
            ),
            PatientAppointment(
                "a-rendon-0916",
                RENDON,
                TODAY,
                ClockTime(10, 30),
                30,
                AppointmentStatus.CONFIRMED,
                LYON,
                locationId = DirectoryScript.locationOf("p-rendon"),
                changeCutoffMinutes = DirectoryScript.changeCutoffMinutes("p-rendon"),
            ),
            PatientAppointment(
                "a-pinto-0921",
                PINTO,
                TODAY.plusDays(5),
                ClockTime(9, 0),
                30,
                AppointmentStatus.REQUESTED,
                ALCANTARA,
                locationId = DirectoryScript.locationOf("p-pinto"),
            ),
        )

        /**
         * c3's rating. The design's words name the visit's content, which this app does not carry (ADR-0001), so the
         * sample says something else.
         */
        val RATINGS = mapOf("a-rendon-0902" to SentRating(5, "Muy clara y puntual.", TODAY.plusDays(-14)))

        /** The design's "18.456.789-2" fails módulo 11 (ADR-0044, deviation 9); this one is valid. */
        val PROFILE = PatientProfile(
            givenNames = "Camila",
            familyNames = "Soto",
            rut = requireNotNull(ChileanRut.parse("18.456.789-K")),
            mobile = requireNotNull(PhoneNumber.chileanMobile("+56987654321")),
            email = requireNotNull(EmailAddress.parse("camila.soto@correo.cl")),
            verified = true,
        )
    }
}
