package com.luismejias.lumemedlink.core.care

/**
 * A professional's photo, so the patient recognises who attends them (backend request `0016`, the platform's
 * `ADR-0106` §8). The platform decides who sees it — a patient of a clinic where the professional attends, or anyone
 * the directory shows it to when the professional switched that on — and answers anyone else as if there were none:
 * [PhotoRead.Absent], the initials. Never a screen's own operation (ADR-0067).
 */
internal interface ProfessionalPhotoSource {
    suspend fun professionalPhoto(professionalId: String): PhotoRead
}

/** No professional has a photo: the design's script, and every build without the platform. */
internal object NoProfessionalPhotos : ProfessionalPhotoSource {
    override suspend fun professionalPhoto(professionalId: String): PhotoRead = PhotoRead.Absent
}
