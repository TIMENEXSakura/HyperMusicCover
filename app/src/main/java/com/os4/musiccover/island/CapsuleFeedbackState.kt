package com.os4.musiccover.island

/** A single timeline per hint. Sampling never schedules a delayed restore. */
internal class LyricFeedbackState {
    data class Frame(val word: Float = 0f, val mark: Float = 0f, val cross: Float = 0f,
                     val playback: Float = 1f)
    private var origin = Frame()
    var frame = Frame()
        private set
    private var enabled = false

    fun begin(on: Boolean) {
        enabled = on
        origin = frame
        // A completed hint has returned to the real playback icon. Closing still begins
        // with a check; an interrupted hint continues from the mark currently on screen.
        if (!on && origin.playback >= 1f) origin = Frame(mark = 1f, playback = 0f)
    }

    fun at(ms: Long): Frame {
        val first = if (enabled) Frame(word = 1f, playback = 0f)
            else Frame(mark = 1f, cross = 1f, playback = 0f)
        val mark = Frame(mark = 1f, cross = if (enabled) 0f else 1f, playback = 0f)
        frame = when {
            ms < 160L -> mix(origin, first, ms / 160f)
            ms < 260L -> first
            ms < 440L -> mix(first, mark, (ms - 260L) / 180f)
            ms < 920L -> mark
            else -> mix(mark, Frame(cross = mark.cross), (ms - 920L) / 180f)
        }
        return frame
    }

    fun clear() { frame = Frame(); origin = frame }
    private fun mix(a: Frame, b: Frame, amount: Float): Frame {
        val t = amount.coerceIn(0f, 1f)
        fun lerp(x: Float, y: Float) = x + (y - x) * t
        return Frame(lerp(a.word, b.word), lerp(a.mark, b.mark), lerp(a.cross, b.cross),
            lerp(a.playback, b.playback))
    }
}

internal object CapsuleTextFeedbackState {
    data class Frame(val message: Boolean, val alpha: Float)
    fun at(ms: Long): Frame = when {
        ms < 120L -> Frame(true, (ms / 120f).coerceIn(0f, 1f))
        ms < 900L -> Frame(true, 1f)
        ms < 1020L -> Frame(true, 1f - (ms - 900L) / 120f)
        else -> Frame(false, ((ms - 1020L) / 120f).coerceIn(0f, 1f))
    }
}
