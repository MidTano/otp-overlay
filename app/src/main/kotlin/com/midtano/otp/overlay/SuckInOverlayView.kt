// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.midtano.otp.R
import kotlin.math.hypot

/**
 * Full-screen overlay that "sucks" its dark background toward the
 * centre.
 *
 * Draws a solid dark fill and punches a circular window out of it
 * centred on the screen. The hole's radius grows from 0 to
 * `maxRadius` over [progress] 0..1, producing a reverse
 * circular-reveal effect.
 *
 * - `progress = 0.0` → fully closed (background visible, no hole).
 * - `progress = 1.0` → fully open   (background gone).
 */
class SuckInOverlayView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    style: Int = 0,
) : View(ctx, attrs, style) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var progress: Float = 0f
    private var cx: Float = 0f
    private var cy: Float = 0f
    private var maxRadius: Float = 0f

    init {
        // Hardware layer is required for PorterDuff to compose correctly.
        setLayerType(LAYER_TYPE_HARDWARE, null)

        bgPaint.color = ContextCompat.getColor(ctx, R.color.suck_in_bg)
        bgPaint.style = Paint.Style.FILL

        // The hole is a CLEAR-mode circle that punches the background
        // out via DST_OUT. Colour itself is irrelevant under CLEAR;
        // black is the conventional "no contribution" fill.
        holePaint.color = Color.BLACK
        holePaint.style = Paint.Style.FILL
        holePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    /** Set the suction progress (0 = closed, 1 = fully open). */
    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        // Half-diagonal reaches every corner; the small headroom
        // multiplier ensures the punch-out fully covers the screen
        // even after rounding / sub-pixel placement.
        maxRadius = hypot(w.toDouble(), h.toDouble()).toFloat() / 2f * CORNER_HEADROOM
    }

    override fun onDraw(canvas: Canvas) {
        if (progress >= 1f) return

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (progress > 0f) {
            // Cubic ease-in: slow start, sudden collapse.
            val eased = easeInCubic(progress)
            val holeRadius = maxRadius * eased
            canvas.drawCircle(cx, cy, holeRadius, holePaint)
        }
    }

    private fun easeInCubic(t: Float): Float = t * t * t

    companion object {
        /**
         * Headroom on the half-diagonal so the punch-out circle
         * still covers the corners after sub-pixel placement and
         * device-DPI rounding. 5 % is large enough to absorb any
         * realistic rounding error without making the open-state
         * radius visibly too large.
         */
        private const val CORNER_HEADROOM: Float = 1.05f
    }
}
