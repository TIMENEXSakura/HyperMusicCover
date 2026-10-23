package com.os4.musiccover.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import kotlin.math.min

/** Draw-only feedback: does not change the island morph or the button's touch bounds. */
internal class PressFeedbackButton(context: Context) : ImageButton(context) {
    private var press = 0f
    private var animator: ValueAnimator? = null
    private val feedback = LyricFeedbackState()
    private var feedbackAnimator: ValueAnimator? = null
    private var feedbackGeneration = 0
    private var feedbackInk = Color.WHITE
    private var frameRateBeforeHint: Float? = null
    private var hintDisplayManager: DisplayManager? = null
    private var hintDisplayId = Display.INVALID_DISPLAY
    private val hintDisplayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == hintDisplayId) clearTransientFeedback()
        }
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != hintDisplayId) return
            val on = runCatching {
                hintDisplayManager?.getDisplay(displayId)?.state == Display.STATE_ON
            }.getOrDefault(false)
            if (!on) clearTransientFeedback()
        }
    }
    private val feedbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        strokeCap = Paint.Cap.ROUND
    }
    private val wordGlyph = Path()
    private val wordBounds = RectF()
    private val revealClip = Path()
    private var glyphWidth = -1
    private var glyphHeight = -1
    private var glyphTextSize = -1f

    fun setFeedbackInk(color: Int) { feedbackInk = color; invalidate() }

    fun showLyricStatus(enabled: Boolean, color: Int) {
        // Cancel listeners before cancelling the animator: its end callback must never restore
        // an old hint over a newer one. Preserve geometry and the refresh-rate vote on reversal.
        cancelFeedback(releaseFrameRate = false)
        feedbackInk = color
        feedback.begin(enabled)
        if (!isAttachedToWindow || !isShown || windowVisibility != VISIBLE ||
            !ValueAnimator.areAnimatorsEnabled() || !watchHintDisplay()) {
            clearLabel()
        } else {
            requestHintFrameRate()
            val token = feedbackGeneration
            val hintAnimator = ValueAnimator.ofFloat(0f, feedback.durationMs.toFloat()).apply {
                duration = feedback.durationMs
                interpolator = LinearInterpolator()
                // ValueAnimator runs on the display's animation/vsync clock; no fixed-rate loop.
                addUpdateListener {
                    if (token == feedbackGeneration) {
                        feedback.at((it.animatedValue as Float).toLong())
                        invalidate()
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (token != feedbackGeneration) return
                        feedbackAnimator = null
                        feedback.clear()
                        releaseHintFrameRate()
                        invalidate()
                    }
                })
            }
            feedbackAnimator = hintAnimator
            hintAnimator.start()
        }
        announceForAccessibility(if (enabled) "歌词已开启" else "歌词已关闭")
    }

    /** AOD may leave the lockscreen window visible and focused. Observe display power directly,
     * only while a hint is running, so entering DOZE/OFF clears it even without another frame. */
    private fun watchHintDisplay(): Boolean {
        val target = display ?: return false
        if (target.state != Display.STATE_ON) return false
        if (hintDisplayManager != null && hintDisplayId == target.displayId) return true
        unwatchHintDisplay()
        val manager = context.getSystemService(DisplayManager::class.java) ?: return false
        hintDisplayManager = manager
        hintDisplayId = target.displayId
        return runCatching {
            if (Build.VERSION.SDK_INT >= 36) {
                manager.registerDisplayListener(context.mainExecutor,
                    DisplayManager.EVENT_TYPE_DISPLAY_STATE or DisplayManager.EVENT_TYPE_DISPLAY_REMOVED,
                    hintDisplayListener)
            } else {
                // On API 35 the original listener receives state changes as display changes.
                manager.registerDisplayListener(hintDisplayListener, handler)
            }
            // Catch a transition that occurred between the initial check and registration.
            manager.getDisplay(hintDisplayId)?.state == Display.STATE_ON
        }.getOrDefault(false)
    }

    private fun unwatchHintDisplay() {
        val manager = hintDisplayManager
        hintDisplayManager = null
        hintDisplayId = Display.INVALID_DISPLAY
        if (manager != null) runCatching { manager.unregisterDisplayListener(hintDisplayListener) }
    }

    private fun requestHintFrameRate() {
        if (frameRateBeforeHint != null) return
        // API 35 is the module's minimum. Vote only for this view while its hint is active;
        // the OS still decides the available rate under its display and power policy.
        runCatching {
            val previous = requestedFrameRate
            setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
            frameRateBeforeHint = previous
        }
    }

    private fun releaseHintFrameRate() {
        unwatchHintDisplay()
        val previous = frameRateBeforeHint ?: return
        frameRateBeforeHint = null
        runCatching { setRequestedFrameRate(previous) }
    }

    private fun cancelFeedback(releaseFrameRate: Boolean = true) {
        feedbackGeneration++
        feedbackAnimator?.removeAllUpdateListeners()
        feedbackAnimator?.removeAllListeners()
        feedbackAnimator?.cancel()
        feedbackAnimator = null
        if (releaseFrameRate) releaseHintFrameRate()
    }

    fun clearLabel() {
        cancelFeedback()
        feedback.clear()
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) isPressed = false
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        animator?.cancel()
        val target = if (pressed && isEnabled) 1f else 0f
        if (!isAttachedToWindow || !ValueAnimator.areAnimatorsEnabled()) {
            press = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(press, target).apply {
            duration = if (pressed) 80L else 180L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                press = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val save = canvas.save()
        val scale = (1f - 0.35f * press).coerceIn(0.65f, 1f)
        canvas.scale(scale, scale, width / 2f, height / 2f)
        val frame = feedback.frame
        if (frame.playback >= 1f) {
            super.onDraw(canvas)
        } else if (frame.playback > 0f) {
            val imageLayer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(),
                (frame.playback * 255f).toInt())
            // Always draw the currently bound play/pause icon, never a saved playback state.
            super.onDraw(canvas)
            canvas.restoreToCount(imageLayer)
        }
        if (frame.word > 0f && ensureGlyph()) {
            feedbackPaint.color = feedbackInk
            feedbackPaint.style = Paint.Style.FILL
            feedbackPaint.alpha = (frame.word * (255f - 95f * frame.cross)).toInt()
            val glyphSave = canvas.save()
            clipRevealedWord(canvas, frame.reveal)
            canvas.drawPath(wordGlyph, feedbackPaint)
            canvas.restoreToCount(glyphSave)
            if (frame.cross > 0f) drawCross(canvas, frame)
        }
        canvas.restoreToCount(save)
    }

    /** Text shaping is cached until size or font scale changes; no per-frame bitmap or glyph. */
    private fun ensureGlyph(): Boolean {
        val size = min(18f * resources.displayMetrics.scaledDensity, min(width, height) * 0.78f)
        if (size <= 0f) return false
        if (width == glyphWidth && height == glyphHeight && size == glyphTextSize)
            return !wordBounds.isEmpty
        feedbackPaint.textSize = size
        feedbackPaint.getTextPath("词", 0, 1, 0f, 0f, wordGlyph)
        wordGlyph.computeBounds(wordBounds, true)
        wordGlyph.offset(width / 2f - wordBounds.centerX(), height / 2f - wordBounds.centerY())
        wordGlyph.computeBounds(wordBounds, true)
        glyphWidth = width
        glyphHeight = height
        glyphTextSize = size
        return !wordBounds.isEmpty
    }

    private fun clipRevealedWord(canvas: Canvas, progress: Float) {
        if (progress >= 1f) return
        val edge = LyricFeedbackGeometry.revealEdge(progress)
        val left = wordBounds.left
        val top = wordBounds.top
        val w = wordBounds.width()
        val h = wordBounds.height()
        revealClip.rewind()
        revealClip.moveTo(left, top)
        if (edge <= 1f) {
            revealClip.lineTo(left + w * edge, top)
            revealClip.lineTo(left, top + h * edge)
        } else {
            revealClip.lineTo(wordBounds.right, top)
            revealClip.lineTo(wordBounds.right, top + h * (edge - 1f))
            revealClip.lineTo(left + w * (edge - 1f), wordBounds.bottom)
            revealClip.lineTo(left, wordBounds.bottom)
        }
        revealClip.close()
        canvas.clipPath(revealClip)
    }

    private fun drawCross(canvas: Canvas, frame: LyricFeedbackState.Frame) {
        feedbackPaint.style = Paint.Style.STROKE
        feedbackPaint.strokeWidth = 2.2f * resources.displayMetrics.density
        feedbackPaint.alpha = (frame.word * 255f).toInt()
        val first = LyricFeedbackGeometry.firstCrossStroke(frame.cross)
        val second = LyricFeedbackGeometry.secondCrossStroke(frame.cross)
        val left = wordBounds.left
        val top = wordBounds.top
        val bottom = wordBounds.bottom
        val w = wordBounds.width()
        val h = wordBounds.height()
        if (first > 0f) canvas.drawLine(left, top, left + w * first, top + h * first, feedbackPaint)
        if (second > 0f) canvas.drawLine(left, bottom, left + w * second, bottom - h * second,
            feedbackPaint)
    }

    private fun clearTransientFeedback() {
        isPressed = false
        animator?.cancel()
        animator = null
        press = 0f
        clearLabel()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) clearTransientFeedback()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) clearTransientFeedback()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) clearTransientFeedback()
    }

    override fun onDetachedFromWindow() {
        clearTransientFeedback()
        super.onDetachedFromWindow()
    }
}
