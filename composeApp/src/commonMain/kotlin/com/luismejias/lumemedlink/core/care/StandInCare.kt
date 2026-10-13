package com.luismejias.lumemedlink.core.care

/**
 * The world a waiting screen is drawn with (ADR-0055): the script, answering at once. Its people are the design's
 * synthetic sample, and under the kit's placeholder none of it is read or shown — only its shape is. A new instance
 * each time, because the script keeps what a run did.
 */
internal fun standInPatientCare(): ScriptedPatientCare = ScriptedPatientCare(latency = {})

/** The doctor's side of [standInPatientCare]. */
internal fun standInDoctorCare(): ScriptedDoctorCare = ScriptedDoctorCare(latency = {})
