package com.os4.musiccover.island

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

/** Changes the existing text views' content. Old and new text are never drawn together. */
internal class CapsuleTextFeedback(private val render: () -> Unit) {
    var text: String? = null
        private set
    var alpha = 1f
        private set
    private var animator: ValueAnimator? = null
    private var generation = 0

    fun show(message: String) {
        cancelAnimation()
        val token = generation
        text = message
        alpha = 0f
        render()
        animator = ValueAnimator.ofFloat(0f, 1140f).apply {
            duration = 1140L
            interpolator = LinearInterpolator()
            addUpdateListener {
                if (token == generation) {
                    val frame = CapsuleTextFeedbackState.at((it.animatedValue as Float).toLong())
                    text = message.takeIf { frame.message }
                    alpha = frame.alpha
                    render()
                }
            }
            start()
        }
    }

    private fun cancelAnimation() {
        generation++
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }

    fun clear() {
        cancelAnimation()
        text = null
        alpha = 1f
        render()
    }
}
