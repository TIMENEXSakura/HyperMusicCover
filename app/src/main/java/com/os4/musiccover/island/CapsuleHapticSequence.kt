package com.os4.musiccover.island

/** One response per accepted tap. An unavailable result adds one cancellable second tick. */
internal class CapsuleHapticSequence(
    private val eligible: () -> Boolean,
    private val pulse: () -> Boolean,
    private val schedule: (Runnable, Long) -> Boolean,
    private val remove: (Runnable) -> Unit,
    private val pendingChanged: (Boolean) -> Unit,
) {
    companion object { const val SECOND_TICK_DELAY_MS = 110L }
    private var pending: Runnable? = null

    fun cancel() {
        val task = pending ?: return
        pending = null
        remove(task)
        pendingChanged(false)
    }

    fun click() {
        cancel()
        if (eligible()) pulse()
    }

    fun unavailable() {
        cancel()
        if (!eligible() || !pulse()) return
        val task = object : Runnable {
            override fun run() {
                if (pending !== this) return
                pending = null
                pendingChanged(false)
                if (eligible()) pulse()
            }
        }
        pending = task
        pendingChanged(true)
        if (!schedule(task, SECOND_TICK_DELAY_MS)) cancel()
    }
}
