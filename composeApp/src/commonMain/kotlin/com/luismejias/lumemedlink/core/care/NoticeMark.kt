package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.core.session.SecureStore
import com.luismejias.lumemedlink.core.session.SecureStoreKey
import com.luismejias.lumemedlink.core.session.SecureStoreUnreadableException

/**
 * The newest notice shown, kept by the app because the platform keeps no read mark (its `listMyNotices`). Lives in the
 * secure store's namespace, so the logout erases it (§8.13).
 */
internal class NoticeMark(private val store: SecureStore) {
    /**
     * When the newest notice shown was sent, or null when none was. A mark that cannot be read counts as none: the bell
     * then calls every notice new, which costs a glance and hides nothing.
     */
    suspend fun seenUntil(): Long? = try {
        store.get(SecureStoreKey.NOTICES_SEEN_UNTIL.storageKey)?.toLongOrNull()
    } catch (@Suppress("SwallowedException") unreadable: SecureStoreUnreadableException) {
        null
    }

    /** Moves the mark forward to [epochMillis], never back: an older list shown later does not revive what was seen. */
    suspend fun seen(epochMillis: Long) {
        val now = seenUntil()
        if (now == null ||
            epochMillis > now
        ) {
            store.put(SecureStoreKey.NOTICES_SEEN_UNTIL.storageKey, epochMillis.toString())
        }
    }
}
