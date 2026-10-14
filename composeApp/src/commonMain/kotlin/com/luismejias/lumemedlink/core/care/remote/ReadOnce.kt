package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.networking.AppErrorException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A reference catalog read once and kept in memory: concurrent first readers share one request. A catalog that cannot
 * be read answers [unreadable] and is asked for again next time; what depends on it degrades instead of failing.
 */
internal class ReadOnce<T>(private val unreadable: T, private val read: suspend () -> T) {
    private val value = MutableStateFlow<T?>(null)
    private val reading = Mutex()

    suspend fun get(): T = value.value ?: reading.withLock { value.value ?: load() }

    private suspend fun load(): T = try {
        read().also { value.value = it }
    } catch (@Suppress("SwallowedException") failed: AppErrorException) {
        currentCoroutineContext().ensureActive()
        unreadable
    }
}
