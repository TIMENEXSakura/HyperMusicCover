package com.os4.musiccover.island

/** Geometry and opacity only: the current media drawable remains owned by playback binding. */
internal class LyricFeedbackState {
    companion object {
        const val WORD_DRAW_MS = 320L
        const val CLOSE_WORD_MS = 120L
        const val CROSS_DRAW_MS = 240L
        const val WORD_HOLD_MS = 300L
        const val RESTORE_MS = 180L
    }

    data class Frame(val word: Float = 0f, val reveal: Float = 0f, val cross: Float = 0f,
                     val playback: Float = 1f)
    private var origin = Frame()
    var frame = Frame()
        private set
    private var enabled = false
    val durationMs: Long
        get() = drawingMs + WORD_HOLD_MS + RESTORE_MS
    private val drawingMs: Long
        get() = if (enabled) WORD_DRAW_MS else CLOSE_WORD_MS + CROSS_DRAW_MS

    /** Interruptions start at the last drawn frame, including during the return to playback. */
    fun begin(on: Boolean) {
        enabled = on
        origin = frame
    }

    fun at(ms: Long): Frame {
        val time = ms.coerceAtLeast(0L)
        val written = Frame(word = 1f, reveal = 1f, cross = if (enabled) 0f else 1f,
            playback = 0f)
        val restoreAt = drawingMs + WORD_HOLD_MS
        frame = when {
            time >= durationMs -> Frame()
            enabled && time < WORD_DRAW_MS -> mix(origin, written, time / WORD_DRAW_MS.toFloat())
            !enabled && time < CLOSE_WORD_MS -> mix(origin,
                written.copy(cross = origin.cross), time / CLOSE_WORD_MS.toFloat())
            !enabled && time < drawingMs -> mix(written.copy(cross = origin.cross), written,
                (time - CLOSE_WORD_MS) / CROSS_DRAW_MS.toFloat())
            time < restoreAt -> written
            // Fade the completed drawing without erasing its geometry. A new click can still
            // reverse from this precise partially transparent frame.
            else -> mix(written, written.copy(word = 0f, playback = 1f),
                (time - restoreAt) / RESTORE_MS.toFloat())
        }
        return frame
    }

    fun clear() { frame = Frame(); origin = frame }
    private fun mix(a: Frame, b: Frame, amount: Float): Frame {
        val t = amount.coerceIn(0f, 1f)
        fun lerp(x: Float, y: Float) = x + (y - x) * t
        return Frame(lerp(a.word, b.word), lerp(a.reveal, b.reveal), lerp(a.cross, b.cross),
            lerp(a.playback, b.playback))
    }
}

/** A diagonal ink front on a unit square, then the two strokes of an X in order. */
internal object LyricFeedbackGeometry {
    fun revealEdge(progress: Float) = progress.coerceIn(0f, 1f) * 2f
    fun firstCrossStroke(progress: Float) = (progress * 2f).coerceIn(0f, 1f)
    fun secondCrossStroke(progress: Float) = (progress * 2f - 1f).coerceIn(0f, 1f)
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
