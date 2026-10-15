package com.luismejias.lumemedlink.features.patient.tabs

import com.luismejias.lumemedlink.core.links.DeepLink
import com.luismejias.lumemedlink.core.links.DeepLinkEntry
import com.luismejias.lumemedlink.features.patient.change.ChangeViewModel
import com.luismejias.lumemedlink.features.patient.notices.NoticesViewModel
import com.luismejias.lumemedlink.features.patient.rating.RateViewModel

/**
 * Where a link or a notification lands in the patient's app (ADR-0053). Exhaustive and without `else`: a new catalog
 * entry does not compile until this says where it goes.
 *
 * Whatever was open — an appointment's flow, a rating sheet — closes first, so the link never lands underneath it.
 * Entries whose screen the design does not draw yet land on the closest screen that exists, declared here rather than
 * invented: the notice centre on Inicio (its bell), the settings rows on Configuración.
 */
internal suspend fun openPatientLink(
    link: DeepLink,
    tabs: PatientTabsViewModel,
    change: ChangeViewModel,
    rate: RateViewModel,
    notices: NoticesViewModel,
) {
    change.closeForLink()
    rate.dismiss()
    notices.close()
    when (link.entry) {
        DeepLinkEntry.HOME -> tabs.show(PatientTab.HOME)
        DeepLinkEntry.SEARCH -> tabs.show(PatientTab.SEARCH)
        DeepLinkEntry.APPOINTMENTS -> tabs.show(PatientTab.APPOINTMENTS)
        // c3 shows the stars of an attended visit, so the rating link opens the detail: f5 starts at the star tapped.
        DeepLinkEntry.APPOINTMENT_DETAIL, DeepLinkEntry.APPOINTMENT_RATING -> {
            tabs.show(PatientTab.APPOINTMENTS)
            link.ref?.let { change.openDetailFor(it) }
        }
        DeepLinkEntry.MY_DOCTORS -> tabs.show(PatientTab.DOCTORS)
        DeepLinkEntry.SETTINGS,
        DeepLinkEntry.SETTINGS_PROFILE,
        DeepLinkEntry.SETTINGS_REMINDERS,
        DeepLinkEntry.SETTINGS_RATINGS,
        DeepLinkEntry.SETTINGS_COMPLAINTS,
        DeepLinkEntry.SETTINGS_PRIVACY,
        -> tabs.show(PatientTab.SETTINGS)
        // The list behind the bell; a single notice has no screen of its own, so it opens the list too.
        DeepLinkEntry.NOTICES, DeepLinkEntry.NOTICE_DETAIL -> {
            tabs.show(PatientTab.HOME)
            notices.show()
        }
        // Never resolved to a patient (LinkCenter drops the other role's links).
        DeepLinkEntry.DOCTOR_AGENDA, DeepLinkEntry.DOCTOR_PATIENTS, DeepLinkEntry.DOCTOR_SETTINGS -> Unit
    }
}
