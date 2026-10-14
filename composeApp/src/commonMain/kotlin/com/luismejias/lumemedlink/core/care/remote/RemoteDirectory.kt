package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.BookingFailure
import com.luismejias.lumemedlink.core.care.BookingResult
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CarePlace
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ClosedDay
import com.luismejias.lumemedlink.core.care.ClosedKind
import com.luismejias.lumemedlink.core.care.Commune
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.DirectoryGateway
import com.luismejias.lumemedlink.core.care.NearbyCommune
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.core.care.Specialty
import com.luismejias.lumemedlink.core.networking.AppErrorException
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.BookPatientAppointmentDto
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.DirectoryEntryDto
import com.luismejias.lumemedlink.core.networking.contract.SearchDirectoryDto
import com.luismejias.lumemedlink.shared.AppError
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private val MIDNIGHT = ClockTime(0, 0)

/**
 * Search to booking over the contract (task `0036`): the platform's catalogs, `searchDirectory`, a professional's free
 * hours and closed periods, the holidays, and `bookMyAppointment` with its key. Hours are on each place's wall clock
 * ([zones], ADR-0063). What the contract does not have stays honest: no nearby communes (the design's e5).
 */
internal class RemoteDirectory(
    private val contract: ContractClient,
    private val specialtyNames: SpecialtyNames,
    private val zones: PlaceZones = PlaceZones(),
) : DirectoryGateway {
    private val communeCatalog = ReadOnce(emptyMap()) {
        val regions = contract.call(ContractOperations.LIST_REGIONS).associate { it.code to it.name }
        contract.call(ContractOperations.LIST_COMMUNES)
            .associate { it.code to Commune(it.code, it.name, regions[it.regionCode].orEmpty()) }
    }

    override suspend fun specialties(): CareResult<List<Specialty>> = careRead {
        contract.call(ContractOperations.LIST_SPECIALTIES)
            .filterNot { it.isSuppressed }
            .map { Specialty(it.code, it.name) }
            .sortedBy { it.name }
    }

    /** Only communes where the specialty has a bookable place (e3), named by the platform's territorial catalog. */
    override suspend fun communes(specialtyCode: String): CareResult<List<Commune>> = careRead {
        val found = contract.call(
            ContractOperations.SEARCH_DIRECTORY,
            SearchDirectoryDto(specialtyCode = specialtyCode),
        )
        val catalog = communeCatalog.get()
        found.flatMap { it.locations }
            .filter { it.bookable }
            .mapNotNull { it.communeCode }
            .distinct()
            .mapNotNull(catalog::get)
            .sortedBy { it.name }
    }

    /** One result per bookable place of each professional in [communeCode]: booking names the place. */
    override suspend fun search(specialtyCode: String, communeCode: String): CareResult<List<DirectoryDoctor>> =
        careRead {
            val names = specialtyNames.all()
            val commune = communeCatalog.get()[communeCode]?.name.orEmpty()
            contract.call(
                ContractOperations.SEARCH_DIRECTORY,
                SearchDirectoryDto(specialtyCode = specialtyCode, communeCode = communeCode),
            ).onEach { it.learnZones() }.flatMap { entry -> entry.inCommune(communeCode, commune, names) }
        }

    /** The design's e5 has no contract: no commune is offered as near. */
    override suspend fun nearbyCommunes(specialtyCode: String, communeCode: String): CareResult<List<NearbyCommune>> =
        CareResult.Ok(emptyList())

    /**
     * The professional by id (`getDirectoryProfessional`, since 0.130.0), at their first bookable place: «Agendar de
     * nuevo». `null` when they are no longer in the directory, or take no bookings anywhere.
     */
    override suspend fun doctor(doctorId: String): CareResult<DirectoryDoctor?> = try {
        val names = specialtyNames.all()
        val entry = contract.call(
            ContractOperations.GET_DIRECTORY_PROFESSIONAL,
            path = mapOf("professionalId" to doctorId),
        ).also { it.learnZones() }
        val place = entry.locations.firstOrNull { it.bookable }
        val commune = place?.communeCode?.let { communeCatalog.get()[it]?.name }.orEmpty()
        CareResult.Ok(place?.let { entry.atPlace(it.id, it.name, commune, names) })
    } catch (refused: AppErrorException) {
        currentCoroutineContext().ensureActive()
        if (refused.error == AppError.NotFound) CareResult.Ok(null) else CareResult.Failed(refused.error.careFailure())
    }

    override suspend fun availability(
        doctorId: String,
        locationId: String,
        from: CalendarDate,
        to: CalendarDate,
    ): CareResult<List<Slot>> = careRead {
        contract.call(
            ContractOperations.GET_DIRECTORY_AVAILABILITY,
            path = mapOf("professionalId" to doctorId),
            query = window(locationId, from, to),
        ).map { slot ->
            val wall = wallTimeOf(slot.startsAt, zones.of(locationId))
            Slot(wall.date, wall.time, minutesBetween(slot.startsAt, slot.endsAt))
        }
    }

    /**
     * The days between [from] and [to] the doctor does not attend at [locationId]: each closed period's days, and the
     * holidays, which win when both say it (the design names a holiday).
     */
    override suspend fun closedDays(
        doctorId: String,
        locationId: String,
        from: CalendarDate,
        to: CalendarDate,
    ): CareResult<List<ClosedDay>> = careRead {
        val closed = contract.call(
            ContractOperations.LIST_DIRECTORY_CLOSED_PERIODS,
            path = mapOf("professionalId" to doctorId),
            query = window(locationId, from, to),
        ).flatMap {
            daysTouched(it.startsAt, it.endsAt, zones.of(locationId))
        }.map { ClosedDay(it, ClosedKind.NO_ATTENTION) }
        val holidays = contract.call(
            ContractOperations.LIST_HOLIDAYS,
            query = mapOf("from" to from.iso(), "to" to to.iso()),
        ).map { ClosedDay(parseDate(it.date), ClosedKind.HOLIDAY) }
        (holidays + closed).distinctBy { it.date }.filter { it.date in from..to }.sortedBy { it.date }
    }

    override suspend fun book(doctorId: String, locationId: String, slot: Slot, clientId: String): BookingResult = try {
        val starts = instantOf(slot.date, slot.start, zones.of(locationId))
        val booked = contract.call(
            ContractOperations.BOOK_MY_APPOINTMENT,
            BookPatientAppointmentDto(
                professionalId = doctorId,
                locationId = locationId,
                startsAt = starts,
                endsAt = instantPlusMinutes(starts, slot.minutes),
                clientId = clientId,
            ),
        )
        zones.learn(booked.locationId, booked.timeZone)
        BookingResult.Booked(booked.toPatientAppointment(zones.fallback, specialtyNames.all()))
    } catch (refused: AppErrorException) {
        currentCoroutineContext().ensureActive()
        BookingResult.Failed(refused.error.bookingFailure())
    }

    /** [from] through [to], both included, as the contract's window: from the first day's start to the next day's. */
    private fun window(locationId: String, from: CalendarDate, to: CalendarDate): Map<String, String> = mapOf(
        "locationId" to locationId,
        "from" to instantOf(from, MIDNIGHT, zones.of(locationId)),
        "to" to instantOf(to.plusDays(1), MIDNIGHT, zones.of(locationId)),
    )

    private fun DirectoryEntryDto.learnZones() = locations.forEach { zones.learn(it.id, it.timeZone) }

    private fun DirectoryEntryDto.inCommune(code: String, commune: String, names: Map<String, String>) =
        locations.filter { it.bookable && it.communeCode == code }.map { atPlace(it.id, it.name, commune, names) }

    private fun DirectoryEntryDto.atPlace(
        placeId: String,
        placeName: String,
        commune: String,
        names: Map<String, String>,
    ) = DirectoryDoctor(
        doctor = CareDoctor(
            id = id,
            displayName = listOfNotNull(professionalTitle, givenName, familyName).joinToString(" "),
            specialty = specialtyCode?.let(names::get).orEmpty(),
        ),
        locationId = placeId,
        commune = commune,
        place = CarePlace(address = "", detail = placeName),
        rating = rating?.average,
        ratingCount = rating?.count ?: 0,
        // No contract (request 0008 §4): the design's attentions and next hour are not published.
        attentions = 0,
        nextSlot = null,
    )
}

/** The contract's 409s on a booking, as the design names them. */
internal fun AppError.bookingFailure(): BookingFailure = when (problemCode()) {
    "pending_request_exists" -> BookingFailure.AlreadyPending
    "conflict", "booking_closed", "patient_unavailable" -> BookingFailure.SlotTaken
    else -> if (this is AppError.Retryable && status == null) BookingFailure.Offline else BookingFailure.Unavailable
}

/** `2026-10-15`, the contract's calendar date. */
internal fun CalendarDate.iso(): String =
    "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

/** The contract's `YYYY-MM-DD`. */
internal fun parseDate(text: String): CalendarDate {
    val (year, month, day) = text.split('-').map { it.toInt() }
    return CalendarDate(year, month, day)
}
