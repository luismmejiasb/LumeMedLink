package com.luismejias.lumemedlink.app

import com.luismejias.lumemedlink.core.care.AppointmentChanges
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.care.ContactChangeGateway
import com.luismejias.lumemedlink.core.care.MyDoctorsSource
import com.luismejias.lumemedlink.core.care.NoticesSource
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.PatientProfileSource
import com.luismejias.lumemedlink.core.care.RatingsGateway

/**
 * The patient's side assembled one capability at a time (task `0036`): each comes from wherever it is wired today,
 * the platform or the script, and no screen can tell which. This is the one place that knows.
 */
internal class ComposedPatientCare(
    clock: ClinicClock,
    profile: PatientProfileSource,
    appointments: AppointmentsSource,
    changes: AppointmentChanges,
    doctors: MyDoctorsSource,
    ratings: RatingsGateway,
    notices: NoticesSource,
    contact: ContactChangeGateway,
) : PatientCareGateway,
    ClinicClock by clock,
    PatientProfileSource by profile,
    AppointmentsSource by appointments,
    AppointmentChanges by changes,
    MyDoctorsSource by doctors,
    RatingsGateway by ratings,
    NoticesSource by notices,
    ContactChangeGateway by contact
