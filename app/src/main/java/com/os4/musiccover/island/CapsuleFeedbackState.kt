package com.os4.musiccover.island

/** A single timeline per hint. Sampling never schedules a delayed restore. */
internal class LyricFeedbackState {
    companion object {
        private const val FADE_IN_MS = 160L
        const val WORD_HOLD_MS = 300L
        private const val MARK_START_MS = FADE_IN_MS + WORD_HOLD_MS
        private const val MARK_SHOWN_MS = MARK_START_MS + 180L
        private const val RESTORE_MS = MARK_SHOWN_MS + 480L
        const val DURATION_MS = RESTORE_MS + 180L
    }

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
            ms < FADE_IN_MS -> mix(origin, first, ms / FADE_IN_MS.toFloat())
            ms < MARK_START_MS -> first
            ms < MARK_SHOWN_MS -> mix(first, mark, (ms - MARK_START_MS) / 180f)
            ms < RESTORE_MS -> mark
            else -> mix(mark, Frame(cross = mark.cross), (ms - RESTORE_MS) / 180f)
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
