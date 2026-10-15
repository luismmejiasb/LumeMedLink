package com.luismejias.lumemedlink.features.patient.notices

import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.CareWorld
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class NoticesViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showingTheListSilencesTheBellAndTellsInicio() = runTest {
        val care = ScriptedPatientCare(latency = {})
        assertEquals(CareResult.Ok(2), care.unreadNoticeCount())
        val model = NoticesViewModel(care)

        model.load()

        assertEquals(2, assertIs<CareLoad.Ready<*>>(model.state.value).content.let { (it as List<*>).size })
        assertEquals(true, model.seen.value)
        assertEquals(CareResult.Ok(0), care.unreadNoticeCount())
    }

    @Test
    fun anEmptyListMovesNoMark() = runTest {
        val model = NoticesViewModel(ScriptedPatientCare(world = CareWorld.EMPTY, latency = {}))
        model.load()
        assertEquals(false, model.seen.value)
    }
}
