package com.os4.musiccover.island

/** The return spring can overshoot the folded position. Keep its geometry elastic, but
 * do not run its focus and glass handoff backwards unless a finger is driving it again. */
internal class CapsuleMorphFocus {
    var value = 1f
        private set

    fun sample(progress: Float, returning: Boolean): Float {
        val next = progress.coerceIn(0f, 1f)
        value = if (returning) minOf(value, next) else next
        return value
    }
}

/** A sharp morph frame (radius = 0) still owns the foreground until the morph ends. */
internal fun foregroundBlur(morph: Float, appearance: Float, swap: Float): Float = when {
    morph >= 0f -> morph
    appearance >= 0f -> appearance
    else -> swap
}
