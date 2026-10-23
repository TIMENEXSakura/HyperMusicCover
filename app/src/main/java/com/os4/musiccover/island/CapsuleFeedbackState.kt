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

/** One content slot: fade its current owner out before replacing it, including on interruption. */
internal class CapsuleTextFeedbackState {
    companion object {
        const val FADE_MS = 120L
        const val HOLD_MS = 780L
        const val DURATION_MS = FADE_MS * 4 + HOLD_MS
    }

    data class Frame(val message: String? = null, val alpha: Float = 1f)
    var frame = Frame()
        private set
    private var origin = frame
    private var requested: String? = null

    /** Rebinding the same pending result must not restart its animation. */
    fun begin(message: String): Boolean {
        if (requested == message) return false
        requested = message
        origin = frame
        return true
    }

    fun at(ms: Long): Frame {
        val message = requested ?: return frame
        val time = ms.coerceAtLeast(0L)
        frame = when {
            time < FADE_MS -> origin.copy(alpha = origin.alpha * (1f - time / FADE_MS.toFloat()))
            time < FADE_MS * 2 -> Frame(message, (time - FADE_MS) / FADE_MS.toFloat())
            time < FADE_MS * 2 + HOLD_MS -> Frame(message, 1f)
            time < FADE_MS * 3 + HOLD_MS ->
                Frame(message, 1f - (time - FADE_MS * 2 - HOLD_MS) / FADE_MS.toFloat())
            time < DURATION_MS -> Frame(null, (time - FADE_MS * 3 - HOLD_MS) / FADE_MS.toFloat())
            else -> Frame().also { requested = null }
        }
        return frame
    }

    fun clear() { requested = null; frame = Frame(); origin = frame }
}

/** Combines the row's existing morph alpha with feedback; neither writer can override the other. */
internal object CapsuleStatusContent {
    data class Layers(val wordsAlpha: Float, val artworkAlpha: Float, val message: Boolean)

    fun layers(frame: CapsuleTextFeedbackState.Frame, contentAlpha: Float): Layers {
        val alpha = frame.alpha.coerceIn(0f, 1f) * contentAlpha.coerceIn(0f, 1f)
        return Layers(alpha, if (frame.message == null) alpha else 0f, frame.message != null)
    }

    /** A round island cannot legibly fit the full sentence. Its accessible label stays whole. */
    fun compact(message: String): String = when (message) {
        "已进入导航界面" -> "导航\n已进入"
        "已退出导航界面" -> "导航\n已退出"
        "正在打开导航界面" -> "导航\n打开中"
        "内容尚未就绪" -> "尚未\n就绪"
        else -> message
    }
}
