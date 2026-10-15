package com.luismejias.lumemedlink.features.patient.notices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.NoticesSource
import com.luismejias.lumemedlink.core.care.PatientNotice
import com.luismejias.lumemedlink.core.care.standInPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.StandIn
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * «Notificaciones», behind the bell on «Inicio» (task `0037`). Showing the list is what makes its notices seen: the
 * newest one shown moves the bell's mark, and [seen] tells the host to read «Inicio» again.
 */
internal class NoticesViewModel(private val notices: NoticesSource) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<List<PatientNotice>>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<List<PatientNotice>>> = current.asStateFlow()

    /** The screen's own shape while it waits (ADR-0055). */
    private val standInContent = StandIn<List<PatientNotice>>()
    val standIn: StateFlow<List<PatientNotice>?> = standInContent.content

    private val shown = MutableStateFlow(false)

    /** Whether the list is pushed over «Inicio». */
    val open: StateFlow<Boolean> = shown.asStateFlow()

    private val marked = MutableStateFlow(false)

    /** A list was shown and the bell's mark moved; [seenHandled] once «Inicio» read it again. */
    val seen: StateFlow<Boolean> = marked.asStateFlow()

    fun show() {
        shown.value = true
        refresh()
    }

    fun close() {
        shown.value = false
    }

    fun seenHandled() {
        marked.value = false
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        standInContent.fill { standInPatientCare().notices().toLoad { it } }
        current.markLoading()
        val read = notices.notices()
        current.value = read.toLoad { it }
        val newest = (current.value as? CareLoad.Ready)?.content?.maxOfOrNull { it.sentAtEpochMillis } ?: return
        notices.noticesSeen(newest)
        marked.value = true
    }
}
