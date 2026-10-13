package com.luismejias.lumemedlink.features.doctor.tabs

import com.luismejias.lumemedlink.core.links.DeepLink
import com.luismejias.lumemedlink.core.links.DeepLinkEntry

/** Where a link or a notification lands in the doctor's app (ADR-0053). Exhaustive, like the patient's. */
internal fun openDoctorLink(link: DeepLink, tabs: DoctorTabsViewModel) {
    when (link.entry) {
        DeepLinkEntry.DOCTOR_AGENDA -> tabs.show(DoctorTab.AGENDA)
        DeepLinkEntry.DOCTOR_PATIENTS -> tabs.show(DoctorTab.PATIENTS)
        DeepLinkEntry.DOCTOR_SETTINGS -> tabs.show(DoctorTab.SETTINGS)
        // Not designed yet: the agenda, the doctor's first screen.
        DeepLinkEntry.NOTICES, DeepLinkEntry.NOTICE_DETAIL -> tabs.show(DoctorTab.AGENDA)
        // Never resolved to a doctor (LinkCenter drops the other role's links).
        DeepLinkEntry.HOME,
        DeepLinkEntry.SEARCH,
        DeepLinkEntry.APPOINTMENTS,
        DeepLinkEntry.APPOINTMENT_DETAIL,
        DeepLinkEntry.APPOINTMENT_RATING,
        DeepLinkEntry.MY_DOCTORS,
        DeepLinkEntry.SETTINGS,
        DeepLinkEntry.SETTINGS_PROFILE,
        DeepLinkEntry.SETTINGS_REMINDERS,
        DeepLinkEntry.SETTINGS_RATINGS,
        DeepLinkEntry.SETTINGS_COMPLAINTS,
        DeepLinkEntry.SETTINGS_PRIVACY,
        -> Unit
    }
}
