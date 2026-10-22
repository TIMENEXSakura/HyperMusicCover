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
    private var label: String? = null
    private var labelAmount = 0f
    private var labelAnimator: ValueAnimator? = null
    private val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val restoreLabel = Runnable {
        labelAnimator?.cancel()
        labelAnimator = ValueAnimator.ofFloat(labelAmount, 0f).apply {
            duration = 180L
            addUpdateListener { labelAmount = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun showLabel(text: String, color: Int) {
        removeCallbacks(restoreLabel)
        labelAnimator?.cancel()
        label = text
        labelPaint.color = color
        labelAmount = 1f
        invalidate()
        postDelayed(restoreLabel, 700L)
    }

    fun clearLabel() {
        removeCallbacks(restoreLabel)
        labelAnimator?.cancel()
        labelAnimator = null
        label = null
        labelAmount = 0f
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
        val imageLayer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(),
            ((1f - labelAmount) * 255f).toInt())
        super.onDraw(canvas)
        canvas.restoreToCount(imageLayer)
        if (labelAmount > 0f) {
            labelPaint.textSize = 18f * resources.displayMetrics.scaledDensity
            labelPaint.alpha = (labelAmount * 255f).toInt()
            val baseline = height / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f
            canvas.drawText(label.orEmpty(), width / 2f, baseline, labelPaint)
        }
        canvas.restoreToCount(save)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        press = 0f
        clearLabel()
        super.onDetachedFromWindow()
    }
}
