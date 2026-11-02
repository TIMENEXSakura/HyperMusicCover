package com.os4.musiccover.island

import java.util.concurrent.atomic.AtomicLong

/** Orders notification events before main-thread delivery, including delayed and reordered posts. */
internal class ReleasedNotificationGrace {
    private val serial = AtomicLong()
    private val removed = HashMap<String, Long>()
    private val posted = HashMap<String, Long>()
    /** Events already in flight when a card is captured/released belong to the older state. */
    private val floor = HashMap<String, Long>()

    fun nextEvent(): Long = serial.incrementAndGet()

    /** Called on the main thread; false if a later removal was already delivered. */
    fun removed(key: String, event: Long): Boolean {
        if (event <= (floor[key] ?: 0L) || event <= (removed[key] ?: 0L)) return false
        removed[key] = event
        return true
    }

    fun posted(key: String, event: Long) {
        if (event > (floor[key] ?: 0L) && event > (posted[key] ?: 0L)) posted[key] = event
    }

    /** Expire exactly this removal, ignoring it if another removal or release superseded it. */
    fun expired(key: String, event: Long): Boolean {
        if (removed[key] != event) return false
        removed.remove(key)
        val absent = (posted.remove(key) ?: 0L) < event
        return absent
    }

    fun forget(key: String) {
        removed.remove(key)
        posted.remove(key)
        floor[key] = serial.get()
    }

    fun prune(keys: Set<String>) {
        removed.keys.retainAll(keys)
        posted.keys.retainAll(keys)
        floor.keys.retainAll(keys)
    }
}
