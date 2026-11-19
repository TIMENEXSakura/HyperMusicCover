package com.os4.musiccover.island

/** For one switch, or for its retained outgoing ghost. Geometry can spring back after
 * arrival; focus follows the outward trip only. A new switch starts its own pulse. */
internal class SwapBlurProgress {
    private var furthest = 0f

    fun advance(progress: Float): Float {
        furthest = maxOf(furthest, progress.coerceIn(0f, 1f))
        return furthest
    }

    fun pulse(progress: Float): Float {
        val p = advance(progress)
        return if (p == 1f) 0f else kotlin.math.sin(Math.PI.toFloat() * p)
    }
}
