package com.os4.musiccover.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton

/** Draw-only press feedback: does not overwrite the island morph's scale, alpha or listeners. */
internal class PressFeedbackButton(context: Context) : ImageButton(context) {
    private var press = 0f
    private var animator: ValueAnimator? = null
    private val feedback = LyricFeedbackState()
    private var feedbackAnimator: ValueAnimator? = null
    private var feedbackGeneration = 0
    private var feedbackInk = android.graphics.Color.WHITE
    private val feedbackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    fun setFeedbackInk(color: Int) { feedbackInk = color; invalidate() }

    fun showLyricStatus(enabled: Boolean, color: Int) {
        cancelFeedback()
        feedbackInk = color
        feedback.begin(enabled)
        val token = feedbackGeneration
        feedbackAnimator = ValueAnimator.ofFloat(0f, LyricFeedbackState.DURATION_MS.toFloat()).apply {
            duration = LyricFeedbackState.DURATION_MS
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                if (token == feedbackGeneration) {
                    feedback.at((it.animatedValue as Float).toLong())
                    invalidate()
                }
            }
            start()
        }
        announceForAccessibility(if (enabled) "歌词已开启" else "歌词已关闭")
    }

    private fun cancelFeedback() {
        feedbackGeneration++
        feedbackAnimator?.removeAllUpdateListeners()
        feedbackAnimator?.cancel()
        feedbackAnimator = null
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
        if (frame.playback > 0f) {
            val imageLayer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(),
                (frame.playback * 255f).toInt())
            // The underlying drawable always comes from the latest playback state.
            super.onDraw(canvas)
            canvas.restoreToCount(imageLayer)
        }
        feedbackPaint.style = android.graphics.Paint.Style.FILL
        feedbackPaint.color = feedbackInk
        if (frame.word > 0f) {
            feedbackPaint.textSize = 18f * resources.displayMetrics.scaledDensity
            feedbackPaint.alpha = (frame.word * 255f).toInt()
            val baseline = height / 2f - (feedbackPaint.ascent() + feedbackPaint.descent()) / 2f
            canvas.drawText("词", width / 2f, baseline, feedbackPaint)
        }
        if (frame.mark > 0f) {
            val c = frame.cross
            feedbackPaint.alpha = (frame.mark * (255f - 80f * c)).toInt()
            feedbackPaint.style = android.graphics.Paint.Style.STROKE
            feedbackPaint.strokeWidth = 2.2f * resources.displayMetrics.density
            val d = resources.displayMetrics.density
            fun x(a: Float, b: Float) = width / 2f + (a + (b - a) * c) * d
            fun y(a: Float, b: Float) = height / 2f + (a + (b - a) * c) * d
            canvas.drawLine(x(-6f, -5f), y(0f, -5f), x(-1f, 5f), y(5f, 5f), feedbackPaint)
            canvas.drawLine(x(-1f, -5f), y(5f, 5f), x(8f, 5f), y(-6f, -5f), feedbackPaint)
        }
        canvas.restoreToCount(save)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) isPressed = false
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        press = 0f
        clearLabel()
        super.onDetachedFromWindow()
    }
}
