package com.os4.musiccover.island

import com.os4.musiccover.Gen

import android.view.Display
import android.view.HapticFeedbackConstants
import android.view.View

/** Uses the system's normal touch feedback, without bypassing device/user settings. */
internal class CapsuleHaptics(private val view: View, private val enabled: () -> Boolean) {
    private val detachListener: View.OnAttachStateChangeListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = cancel()
    }
    private val sequence: CapsuleHapticSequence = CapsuleHapticSequence(
        eligible = {
            enabled() && view.isAttachedToWindow && view.isShown &&
                view.windowVisibility == View.VISIBLE && view.display?.state == Display.STATE_ON
        },
        pulse = { runCatching { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }.getOrDefault(false) },
        schedule = { task, delay -> Gen.postDelayed(task, delay) },
        remove = { task -> Gen.removeCallbacks(task) },
        pendingChanged = { active ->
            if (active) Gen.attachState(view, detachListener)
            else Gen.unattachState(view, detachListener)
        },
    )

    fun click() = sequence.click()
    fun unavailable() = sequence.unavailable()
    fun cancel(): Unit = sequence.cancel()
}
