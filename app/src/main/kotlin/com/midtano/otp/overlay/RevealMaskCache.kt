// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Cached [RadialGradient] mask used by the reveal composite in
 * [OtpRevealLayout]. The geometry only changes on resize, so the
 * gradient is built once and reused across frames.
 */
internal class RevealMaskCache {

    private var cached: RadialGradient? = null
    private var cachedW: Int = 0
    private var cachedH: Int = 0

    /** Invalidate when geometry changes (e.g. `onSizeChanged`). */
    fun invalidate() {
        cached = null
        cachedW = 0
        cachedH = 0
    }

    /**
     * Fetch (or rebuild) the radial mask for the current frame and
     * install it on [maskPaint] as a shader.
     */
    fun install(maskPaint: Paint, w: Int, h: Int, cx: Float, cy: Float, maxRadius: Float) {
        val needsRebuild = cached == null || cachedW != w || cachedH != h
        if (needsRebuild) {
            val maskInner = maxRadius * 0.45f
            val maskOuter = maxRadius * 1.45f
            cached = RadialGradient(
                cx, cy, maskOuter,
                intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000, 0x00000000),
                floatArrayOf(0f, maskInner / maskOuter, 0.92f, 1f),
                Shader.TileMode.CLAMP,
            )
            cachedW = w
            cachedH = h
        }
        maskPaint.shader = cached
    }
}
