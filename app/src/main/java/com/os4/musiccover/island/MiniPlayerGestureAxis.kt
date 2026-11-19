package com.os4.musiccover.island

import kotlin.math.abs
import kotlin.math.hypot

/** Decide once per finger after touch slop; a later curve or reversal cannot change its axis. */
internal enum class MiniPlayerGestureAxis { UNDECIDED, HORIZONTAL, VERTICAL }

internal fun MiniPlayerGestureAxis.resolve(dx: Float, dy: Float, slop: Float): MiniPlayerGestureAxis {
    if (this != MiniPlayerGestureAxis.UNDECIDED) return this
    if (!dx.isFinite() || !dy.isFinite() || !slop.isFinite() || hypot(dx, dy) <= slop.coerceAtLeast(0f)) return this
    return if (abs(dx) > abs(dy)) MiniPlayerGestureAxis.HORIZONTAL else MiniPlayerGestureAxis.VERTICAL
}

/** Only motions that may move a card vertically invalidate the folded lyric band. */
internal fun lyricBandMotionUnstable(stackSwap: Boolean, morph: Boolean, group: Boolean,
                                     flight: Boolean, exchange: Boolean, noteMorph: Boolean,
                                     landing: Boolean, spread: Boolean): Boolean =
    stackSwap || morph || group || flight || exchange || noteMorph || landing || spread
