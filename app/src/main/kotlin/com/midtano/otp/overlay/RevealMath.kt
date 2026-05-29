// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

/**
 * Pure math helpers for [OtpRevealLayout]'s reveal / dismiss
 * pipeline. No state, no Android views — just functions the host
 * delegates to.
 */
internal object RevealMath {

    /**
     * Linear stage progress between [start] and [stop] bounds. Returns
     * 0 for `t <= start`, 1 for `t >= stop`, linear interpolation in
     * between.
     */
    fun stage(t: Float, start: Float, stop: Float): Float = when {
        t <= start -> 0f
        t >= stop -> 1f
        else -> (t - start) / (stop - start)
    }

    /** Apply a new alpha to an `0xAARRGGBB` colour, preserving RGB. */
    fun withAlpha(colour: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (colour and 0x00FFFFFF) or (a shl 24)
    }

    /** Linear interpolation. */
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Clamp [v] to the inclusive `[lo, hi]` range. NaN passes through
     * unchanged because no comparison succeeds.
     */
    fun clamp(v: Float, lo: Float, hi: Float): Float = when {
        v < lo -> lo
        v > hi -> hi
        else -> v
    }
}
