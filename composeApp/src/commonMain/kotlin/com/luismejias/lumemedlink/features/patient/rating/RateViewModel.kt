package com.luismejias.lumemedlink.features.patient.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.RatingAspect
import com.luismejias.lumemedlink.core.care.RatingDraft
import com.luismejias.lumemedlink.core.care.RatingPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How the visit went, by its stars (f5a–f5c): each level asks something else. */
internal enum class RatingLevel(val aspects: List<RatingAspect>) {
    /** 1–2 stars: what failed, and the road to a formal complaint. */
    BAD(
        listOf(
            RatingAspect.LONG_WAIT,
            RatingAspect.NOT_EXPLAINED,
            RatingAspect.UNKIND,
            RatingAspect.UNEXPECTED_CHARGE,
            RatingAspect.MOVED_HOUR,
        ),
    ),

    /** 3 stars: what could be better, in a constructive tone. */
    FAIR(
        listOf(
            RatingAspect.PUNCTUALITY,
            RatingAspect.CLARITY,
            RatingAspect.VISIT_TIME,
            RatingAspect.NOTICE,
            RatingAspect.FACILITIES,
        ),
    ),

    /** 4–5 stars: what to highlight on the doctor's profile. */
    GOOD(
        listOf(
            RatingAspect.EXPLAINED_CLEARLY,
            RatingAspect.KIND,
            RatingAspect.PUNCTUALITY,
            RatingAspect.SOLVED_IT,
            RatingAspect.FACILITIES,
        ),
    ),
    ;

    companion object {
        fun of(stars: Int): RatingLevel = when {
            stars <= 2 -> BAD
            stars == 3 -> FAIR
            else -> GOOD
        }
    }
}

/** f5's sheet: the stars, the aspects of their level, «Otro» with its own words, and «Cuéntanos más». */
internal data class RateState(
    val prompt: RatingPrompt,
    val stars: Int,
    val aspects: Set<RatingAspect> = emptySet(),
    val otherOn: Boolean = false,
    val other: String = "",
    val comment: String = "",
    val failure: CareFailure? = null,
) {
    val level: RatingLevel get() = RatingLevel.of(stars)

    override fun toString(): String = "RateState(stars=$stars, aspects=${aspects.size})"
}

/**
 * f5's owner, opened from Inicio's stars or from an attended visit's (c3). PROVISIONAL (ADR-0046): the free text is the
 * design's, by the author's call for this mock; the contract will carry stars and closed reasons only.
 */
internal class RateViewModel(private val care: PatientCareGateway) : ViewModel() {
    private val current = MutableStateFlow<RateState?>(null)
    val state: StateFlow<RateState?> = current.asStateFlow()

    private val sentFlag = MutableStateFlow(false)
    val sent: StateFlow<Boolean> = sentFlag.asStateFlow()

    fun launch(step: suspend RateViewModel.() -> Unit) {
        viewModelScope.launch { step() }
    }

    /** Tapping a star opens the sheet at that level (spec f5). */
    fun open(prompt: RatingPrompt, stars: Int) {
        current.value = RateState(prompt, stars.coerceIn(1, MAX_STARS))
    }

    /** A new level keeps only the aspects it also offers. */
    fun starsChanged(stars: Int) = update {
        val next = RatingLevel.of(stars)
        copy(stars = stars.coerceIn(1, MAX_STARS), aspects = aspects.filter { it in next.aspects }.toSet())
    }

    fun aspectToggled(aspect: RatingAspect) = update {
        copy(aspects = if (aspect in aspects) aspects - aspect else aspects + aspect)
    }

    /** Unmarking «Otro» drops its field and its words (spec f5d). */
    fun otherToggled() = update { copy(otherOn = !otherOn, other = "") }

    fun otherChanged(text: String) = update { copy(other = text.take(OTHER_LIMIT)) }

    fun commentChanged(text: String) = update { copy(comment = text) }

    suspend fun send() {
        val state = current.value ?: return
        val draft = RatingDraft(
            stars = state.stars,
            aspects = state.aspects,
            other = state.other.trim().takeIf { state.otherOn && it.isNotEmpty() },
            comment = state.comment.trim().takeIf { it.isNotEmpty() },
        )
        when (val result = care.rate(state.prompt.appointmentId, draft)) {
            is CareResult.Ok -> {
                current.value = null
                sentFlag.value = true
            }
            is CareResult.Failed -> update { copy(failure = result.failure) }
        }
    }

    fun dismiss() {
        current.value = null
    }

    fun sentSeen() {
        sentFlag.value = false
    }

    fun failureSeen() = update { copy(failure = null) }

    private fun update(change: RateState.() -> RateState) {
        current.value = current.value?.change()
    }

    internal companion object {
        const val MAX_STARS = 5

        /** «Máximo 40 caracteres, con contador» (spec f5d). */
        const val OTHER_LIMIT = 40
    }
}
