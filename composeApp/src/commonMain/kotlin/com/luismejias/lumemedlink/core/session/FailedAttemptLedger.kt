package com.luismejias.lumemedlink.core.session

/**
 * The failed-unlock count, kept in the tier-1 store so it outlives the process (ADR-0034).
 *
 * It used to be a `private var` inside [SessionLock], and [SessionLock] lives in a composition: killing
 * the process — or anything that recreates the composition — reset it, so the ceiling this app
 * believed it enforced was not the one enforced.
 *
 * Kept in [SecureStore] rather than in plain preferences on purpose, and no gate would notice the
 * difference: inside `core/` the storage gates exempt plain storage, so this is §8.4 held by hand.
 * The store is also where the logout erases the whole namespace, which is what gives the next
 * session a full budget without anybody having to remember to.
 *
 * Every operation THROWS when the store cannot answer; deciding what that means is [SessionLock]'s
 * job, and it decides to fail closed.
 */
internal class FailedAttemptLedger(private val store: SecureStore) {
    private val key = SecureStoreKey.FAILED_UNLOCK_ATTEMPTS.storageKey

    /**
     * The attempts already spent: 0 when nothing was ever written. A value that is not a
     * non-negative number throws instead of reading as 0 — a corrupt count must never become a
     * fresh budget.
     */
    suspend fun spent(): Int {
        val raw = store.get(key) ?: return 0
        return checkNotNull(raw.toIntOrNull()?.takeIf { it >= 0 }) { "the attempt count is unreadable" }
    }

    suspend fun record(spent: Int) {
        store.put(key, spent.toString())
    }

    suspend fun clear() {
        store.remove(key)
    }
}
