package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime

/**
 * The directory the script answers with (ADR-0046) — synthetic doctors, the design's own sample. A namespace of
 * constants and pure functions, no state: what a booking changes lives in the [ScriptedPatientCare] instance.
 *
 * The edges, for anyone driving the app by hand (task `0030`):
 * - Medicina general in Providencia: three doctors (p3).
 * - Cardiología in Vitacura: nobody, with two nearby communes (e5).
 * - Anything in Ñuñoa: no connection (e6).
 * - Booking Dra. Rendón on Monday 21 at 10:00: the hour was just taken (409).
 * - Booking a doctor twice: one pending request per doctor (409).
 */
internal object DirectoryScript {
    const val OFFLINE_COMMUNE = "13120"
    private const val SLOT_MINUTES = 30
    private const val BOOKING_HORIZON_DAYS = 30
    private const val SATURDAY = 6

    val SPECIALTIES = listOf(
        Specialty("cardio", "Cardiología", listOf("corazón")),
        Specialty("derma", "Dermatología", listOf("piel")),
        Specialty("derma-ped", "Dermatología pediátrica", listOf("piel")),
        Specialty("endo", "Endocrinología", listOf("tiroides")),
        Specialty("gastro", "Gastroenterología", listOf("estómago")),
        Specialty("gine", "Ginecología"),
        Specialty("mfam", "Medicina familiar"),
        Specialty("mgen", "Medicina general"),
        Specialty("nefro", "Nefrología", listOf("riñón")),
        Specialty("neuro", "Neurología"),
        Specialty("pedia", "Pediatría", listOf("niños")),
        Specialty("trauma", "Traumatología", listOf("huesos")),
    )

    private const val METROPOLITANA = "Región Metropolitana"
    private const val VALPARAISO = "Región de Valparaíso"

    val COMMUNES = listOf(
        Commune("13114", "Las Condes", METROPOLITANA),
        Commune(OFFLINE_COMMUNE, "Ñuñoa", METROPOLITANA),
        Commune("13123", "Providencia", METROPOLITANA),
        Commune("13101", "Santiago", METROPOLITANA),
        Commune("13132", "Vitacura", METROPOLITANA),
        Commune("5109", "Viña del Mar", VALPARAISO),
        Commune("5101", "Valparaíso", VALPARAISO),
    )

    private val LYON = CarePlace("Av. Providencia 1208, oficina 504", "Centro Médico Lyon · Providencia, Santiago")
    private val PEDRO = CarePlace("Pedro de Valdivia 61, piso 3", "Centro Pedro de Valdivia · Providencia, Santiago")
    private val ALCANTARA = CarePlace("Alcántara 200, piso 4", "Clínica Alcántara · Las Condes, Santiago")

    private val RENDON = CareDoctor("p-rendon", "Dra.", "Mariana", "Rendón", "Medicina Familiar")
    private val CONTRERAS = CareDoctor("p-contreras", "Dr.", "Andrés", "Contreras", "Medicina General")
    private val VERA = CareDoctor("p-vera", "Dra.", "Paula", "Vera", "Medicina General")
    private val SALAS = CareDoctor("p-salas", "Dr.", "Tomás", "Salas", "Cardiología")
    private val IBANEZ = CareDoctor("p-ibanez", "Dra.", "Inés", "Ibáñez", "Cardiología")

    private class Listing(
        val specialty: String,
        val commune: String,
        val doctor: CareDoctor,
        val place: CarePlace,
        val rating: Double,
        val ratingCount: Int,
        val attentions: Int,
        /** Days after today of the first hour shown as «Próxima hora». */
        val firstDayOffset: Int,
    )

    private val LISTINGS = listOf(
        Listing("mgen", "13123", RENDON, LYON, 4.9, 128, 640, 1),
        Listing("mgen", "13123", CONTRERAS, PEDRO, 4.7, 86, 310, 2),
        Listing("mgen", "13123", VERA, LYON, 4.8, 41, 120, 5),
        Listing("cardio", "13114", SALAS, ALCANTARA, 4.6, 52, 200, 1),
        Listing("cardio", "13123", IBANEZ, PEDRO, 4.8, 33, 90, 5),
    )

    fun communesWith(specialty: String): List<Commune> {
        val withListings = LISTINGS.filter { it.specialty == specialty }.map { it.commune }.toSet()
        // The design's e3 lists every commune of the region; the script keeps them all so e5 is reachable.
        return if (withListings.isEmpty()) emptyList() else COMMUNES
    }

    fun doctorsIn(specialty: String, commune: String, today: CalendarDate, taken: (String, Slot) -> Boolean) =
        LISTINGS.filter { it.specialty == specialty && it.commune == commune }.map { listing ->
            DirectoryDoctor(
                doctor = listing.doctor,
                locationId = "${listing.doctor.id}@${listing.commune}",
                commune = COMMUNES.first { it.code == listing.commune }.name,
                place = listing.place,
                rating = listing.rating,
                ratingCount = listing.ratingCount,
                attentions = listing.attentions,
                nextSlot = slots(listing.doctor.id, today, today.plusDays(BOOKING_HORIZON_DAYS))
                    .firstOrNull { !taken(listing.doctor.id, it) && it.date >= today.plusDays(listing.firstDayOffset) },
            )
        }

    fun nearby(specialty: String, commune: String, today: CalendarDate): List<NearbyCommune> =
        LISTINGS.filter { it.specialty == specialty && it.commune != commune }
            .groupBy { it.commune }
            .map { (code, listings) ->
                NearbyCommune(
                    COMMUNES.first { it.code == code },
                    listings.size,
                    today.plusDays(listings.minOf { it.firstDayOffset }),
                )
            }

    /** Where a doctor attends, for an appointment made by booking them. */
    fun placeOf(doctorId: String): CarePlace = LISTINGS.first { it.doctor.id == doctorId }.place

    fun doctorOf(doctorId: String): CareDoctor = LISTINGS.first { it.doctor.id == doctorId }.doctor

    /**
     * Every hour a doctor publishes between two days, weekdays only: mornings 09:00–11:30 and afternoons
     * 15:00–16:00, each 30 minutes. A few are always taken, so the grid has gaps as the design draws it.
     */
    @Suppress("UnusedParameter") // a real directory answers per doctor; the script's hours are everyone's
    fun slots(doctorId: String, from: CalendarDate, to: CalendarDate): List<Slot> {
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }
        return days.filter { it.isoWeekday < SATURDAY }.flatMap { day ->
            HOURS.map { Slot(day, it, SLOT_MINUTES) }.filterNot { it.start in TAKEN }
        }.toList()
    }

    /** The race of a booking: free when read, taken when booked. */
    fun takenOnArrival(doctorId: String, slot: Slot, today: CalendarDate): Boolean =
        doctorId == RENDON.id && slot.date == today.plusDays(5) && slot.start == ClockTime(10, 0)

    private val HOURS = listOf(
        ClockTime(9, 0), ClockTime(9, 30), ClockTime(10, 0), ClockTime(10, 30), ClockTime(11, 0), ClockTime(11, 30),
        ClockTime(15, 0), ClockTime(15, 30), ClockTime(16, 0),
    )

    /** The design's a1: 09:30, 10:30 and 16:00 are taken, every day, for everyone. */
    private val TAKEN = setOf(ClockTime(9, 30), ClockTime(10, 30), ClockTime(16, 0))
}
