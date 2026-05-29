// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds the closed colour ring used by [OtpRevealLayout]'s sweep
 * gradient.
 *
 * Hue, value and saturation oscillate around their bases on
 * counter-phased sine / cosine curves so the ring reads like a 3-D
 * ribbon catching light rather than a flat painted ring. The last
 * entry duplicates the first so the [android.graphics.SweepGradient]
 * seam between 360° and 0° is invisible.
 */
internal object SweepLoopBuilder {

    /**
     * Compute a fresh sweep ring.
     *
     * @param stops      1..10 — final loop length is
     *                   `max(2, stops) + 1` so the seam is always
     *                   closed.
     * @param hueRange   0..180° peak-to-peak hue oscillation. The
     *                   ring sweeps ±half this around the base hue.
     * @param brandColor dominant launcher-icon colour, or `0` to fall
     *                   back to the default indigo palette.
     * @return colour array suitable for [android.graphics.SweepGradient].
     */
    fun build(stops: Int, hueRange: Float, brandColor: Int): IntArray {
        val s = stops.coerceIn(1, 10)
        // SweepGradient needs at least 2 entries; with stops=1 we
        // duplicate the colour and produce a uniform ring.
        val n = maxOf(2, s)
        val loop = IntArray(n + 1)

        if (brandColor == 0) {
            // Default indigo (~247°) routed through the same generator
            // so the stops count still applies.
            generateLoop(loop, 247f, 0.65f, hueRange)
        } else {
            val hsv = FloatArray(3)
            Color.colorToHSV(brandColor, hsv)
            val sat = hsv[1].coerceIn(0.45f, 0.85f)
            generateLoop(loop, hsv[0], sat, hueRange)
        }
        return loop
    }

    private fun generateLoop(
        loop: IntArray,
        baseHue: Float,
        saturation: Float,
        hueRangeDeg: Float,
    ) {
        val n = loop.size - 1
        val halfRange = hueRangeDeg.coerceIn(0f, 180f) * 0.5f
        for (i in 0 until n) {
            val t = i.toFloat() / n.toFloat()
            // Hue: full sine sweep over the loop, hitting +halfRange
            // peak once and -halfRange peak once per ring.
            val hueDelta = halfRange * sin(t * 2.0 * PI).toFloat()
            val h = wrapHue(baseHue + hueDelta)
            // Value: counter-phased cosine so the brightest stop falls
            // between hue extremes.
            val v = 0.55f + 0.25f * cos(t * 2.0 * PI).toFloat()
            // Saturation eases off slightly near the lightness peaks
            // so they read as bright highlights rather than neon spots.
            val sx = saturation * (0.85f + 0.15f * sin(t * 2.0 * PI + PI / 2).toFloat())
            loop[i] = hsvColor(h, sx, v)
        }
        // Close the seam.
        loop[n] = loop[0]
    }

    fun wrapHue(hueIn: Float): Float {
        var h = hueIn % 360f
        if (h < 0f) h += 360f
        return h
    }

    fun hsvColor(h: Float, s: Float, v: Float): Int {
        val arr = floatArrayOf(wrapHue(h), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        return 0xFF000000.toInt() or (Color.HSVToColor(arr) and 0x00FFFFFF)
    }
}
