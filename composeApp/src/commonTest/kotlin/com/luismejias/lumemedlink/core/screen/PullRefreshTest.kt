package com.luismejias.lumemedlink.core.screen

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullRefreshTest {
    @Test
    fun aPullDuringARunIsIgnoredAndTheFlagFollowsTheRun() = runTest {
        val pull = PullRefresh()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        val first = launch {
            pull.run {
                reads++
                release.await()
            }
        }
        yield()
        assertTrue(pull.refreshing.value, "running while the read waits")
        pull.run { reads++ }
        release.complete(Unit)
        first.join()
        assertEquals(1, reads, "the second pull did not read")
        assertFalse(pull.refreshing.value)
    }

    @Test
    fun aFailedReadStillEndsTheRun() = runTest {
        val pull = PullRefresh()
        assertFailsWith<IllegalStateException> { pull.run { error("synthetic") } }
        assertFalse(pull.refreshing.value, "a stuck spinner would refuse every later pull")
    }
}
