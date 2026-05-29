// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

/**
 * Two-pass `DST_IN` edge fade applied to [OtpRevealLayout] so any
 * chromatic glow bleeding past the card fades softly into the host
 * bounds rather than clipping at the layout edge.
 *
 * Owns its own [LinearGradient] cache so the gradients are built
 * once per resize. Consumes the host's pre-allocated `DST_IN` mask
 * [Paint] via [apply].
 */
internal class EdgeFadeMask {

    private var cachedVFade: LinearGradient? = null
    private var cachedVFadeH: Int = 0

    /**
     * Horizontal companion to [cachedVFade] so the edge fade also
     * feathers left/right edges (matters on the narrow auto-paste
     * pill). Cached separately because width and height differ.
     */
    private var cachedHFade: LinearGradient? = null
    private var cachedHFadeW: Int = 0

    /** Drop the cached gradients — call when paddings or `compact` changes. */
    fun invalidate() {
        cachedVFade = null
        cachedHFade = null
        cachedVFadeH = 0
        cachedHFadeW = 0
    }

    /**
     * Apply the vertical pass then the horizontal pass.
     *
     * @param canvas    the host canvas
     * @param maskPaint pre-configured `DST_IN` paint owned by the host
     * @param w         layout width
     * @param h         layout height
     * @param padTop    [android.view.View.getPaddingTop]
     * @param padBot    [android.view.View.getPaddingBottom]
     * @param padL      [android.view.View.getPaddingLeft]
     * @param padR      [android.view.View.getPaddingRight]
     * @param compact   `true` for the narrow auto-paste pill — uses
     *                  a smoother multi-stop gradient
     * @param edgeFade  user-tunable knob in `[0, 1]`
     */
    fun apply(
        canvas: Canvas,
        maskPaint: Paint,
        w: Int,
        h: Int,
        padTop: Int,
        padBot: Int,
        padL: Int,
        padR: Int,
        compact: Boolean,
        edgeFade: Float,
    ) {
        if (w <= 0 || h <= 0) return
        val smooth = compact

        // Vertical pass.
        if (cachedVFade == null || cachedVFadeH != h) {
            val padV = maxOf(padTop, padBot).toFloat()
            var bandV = if (smooth) {
                clamp(padV / h, 0.16f, 0.42f)
            } else {
                clamp(padV / h, 0.08f, 0.22f)
            }
            bandV *= edgeFade.coerceIn(0f, 1f)
            cachedVFade = if (smooth) {
                val midV = bandV * 0.45f
                LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    intArrayOf(
                        0x00000000, 0x33000000, 0xC4000000.toInt(), 0xFF000000.toInt(),
                        0xFF000000.toInt(),
                        0xC4000000.toInt(), 0x33000000, 0x00000000,
                    ),
                    floatArrayOf(
                        0f, midV, bandV * 0.85f, bandV,
                        1f - bandV,
                        1f - bandV * 0.85f, 1f - midV, 1f,
                    ),
                    Shader.TileMode.CLAMP,
                )
            } else {
                LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    intArrayOf(0x00000000, 0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
                    floatArrayOf(0f, bandV, 1f - bandV, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            cachedVFadeH = h
        }
        maskPaint.shader = cachedVFade
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), maskPaint)

        // Horizontal pass.
        if (cachedHFade == null || cachedHFadeW != w) {
            val padH = maxOf(padL, padR).toFloat()
            var bandH = if (smooth) {
                clamp(padH / w, 0.14f, 0.40f)
            } else {
                clamp(padH / w, 0.06f, 0.22f)
            }
            bandH *= edgeFade.coerceIn(0f, 1f)
            cachedHFade = if (smooth) {
                val midH = bandH * 0.45f
                LinearGradient(
                    0f, 0f, w.toFloat(), 0f,
                    intArrayOf(
                        0x00000000, 0x33000000, 0xC4000000.toInt(), 0xFF000000.toInt(),
                        0xFF000000.toInt(),
                        0xC4000000.toInt(), 0x33000000, 0x00000000,
                    ),
                    floatArrayOf(
                        0f, midH, bandH * 0.85f, bandH,
                        1f - bandH,
                        1f - bandH * 0.85f, 1f - midH, 1f,
                    ),
                    Shader.TileMode.CLAMP,
                )
            } else {
                LinearGradient(
                    0f, 0f, w.toFloat(), 0f,
                    intArrayOf(0x00000000, 0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
                    floatArrayOf(0f, bandH, 1f - bandH, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            cachedHFadeW = w
        }
        maskPaint.shader = cachedHFade
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), maskPaint)
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float = when {
        v < lo -> lo
        v > hi -> hi
        else -> v
    }
}
