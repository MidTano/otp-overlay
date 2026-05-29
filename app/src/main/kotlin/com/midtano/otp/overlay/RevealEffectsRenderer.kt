// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.core.graphics.withTranslation
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stateless renderers for the secondary reveal effects (halo, spark,
 * wave, halftone).
 *
 * Each method takes the canvas plus the per-frame state needed for
 * one effect; no instance fields are mutated, and the host still
 * owns the cached gradients and palette.
 */
internal object RevealEffectsRenderer {

    private val EASE_OUT: Interpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    private val EASE_WAVE: Interpolator = PathInterpolator(0.2f, 0.8f, 0.4f, 1f)

    /**
     * Reused stops/positions arrays. [RadialGradient] copies them
     * defensively into native, so the same buffers can rotate
     * through every halo frame without re-allocating.
     */
    private val haloColors = IntArray(3)
    private val haloPositions = floatArrayOf(0f, 0.55f, 1f)

    /**
     * Soft brand-tinted halo around the card centre. Fades in over
     * the first 35 % of the local time slice and fades out over the
     * remainder.
     */
    fun drawHalo(
        canvas: Canvas,
        haloPaint: Paint,
        cx: Float,
        cy: Float,
        density: Float,
        maxRadius: Float,
        brandBase: Int,
        brandDeep: Int,
        local: Float,
        gAlpha: Float,
    ) {
        if (local <= 0f || local >= 1f) return

        val grow = EASE_OUT.getInterpolation(local)
        val radius = RevealMath.lerp(28f * density, maxRadius * 1.05f, grow)
        var a = if (local < 0.35f) local / 0.35f else 1f - (local - 0.35f) / 0.65f
        a = RevealMath.clamp(a, 0f, 1f) * 0.55f * gAlpha

        haloColors[0] = RevealMath.withAlpha(brandBase, (255 * a).toInt())
        haloColors[1] = RevealMath.withAlpha(brandDeep, (160 * a).toInt())
        haloColors[2] = 0x00000000

        val g = RadialGradient(
            cx,
            cy,
            radius,
            haloColors,
            haloPositions,
            Shader.TileMode.CLAMP,
        )
        haloPaint.shader = g
        canvas.drawCircle(cx, cy, radius, haloPaint)
    }

    /**
     * Rotated-square ("rhombus") chromatic spark with a glow
     * underlay. The path is built by the caller and passed in so
     * it can be cached across frames.
     *
     * The shape was deliberately switched away from a concave
     * four-pointed sparkle to avoid resembling the Google Material
     * "Sparkle" mark; see [buildSparkPath] for the rationale.
     */
    fun drawSpark(
        canvas: Canvas,
        sparkPath: Path,
        sparkPaint: Paint,
        sparkGlow: Paint,
        cx: Float,
        cy: Float,
        density: Float,
        local: Float,
        gAlpha: Float,
    ) {
        if (local <= 0f || local >= 1f) return

        val s = if (local < 0.6f) {
            RevealMath.lerp(0f, 1.15f, EASE_OUT.getInterpolation(local / 0.6f))
        } else {
            RevealMath.lerp(1.15f, 0.85f, (local - 0.6f) / 0.4f)
        }

        val rot = EASE_WAVE.getInterpolation(local) * 360f

        var a = when {
            local < 0.15f -> local / 0.15f
            local < 0.75f -> 1f
            else -> 1f - (local - 0.75f) / 0.25f
        }
        a = RevealMath.clamp(a, 0f, 1f) * gAlpha

        sparkPaint.strokeWidth = 6f * density
        sparkPaint.alpha = (255 * a).toInt()
        sparkGlow.strokeWidth = 14f * density
        sparkGlow.alpha = (140 * a).toInt()

        canvas.withTranslation(cx, cy) {
            scale(s, s)
            rotate(rot)
            translate(-cx, -cy)
            drawPath(sparkPath, sparkGlow)
            drawPath(sparkPath, sparkPaint)
        }
    }

    /**
     * Sweep-gradient wave ring. The [sweepCache] holder lets the
     * caller memoise the gradient across frames; we only update the
     * rotation matrix locally so callers do not have to rebuild on
     * every frame.
     */
    fun drawWave(
        canvas: Canvas,
        waveStroke: Paint,
        sweepCache: Array<SweepGradient?>,
        sweepMatrix: Matrix,
        sweepLoop: IntArray,
        cx: Float,
        cy: Float,
        density: Float,
        maxRadius: Float,
        local: Float,
        gAlpha: Float,
    ) {
        if (local <= 0f || local >= 1f) return

        val grow = EASE_OUT.getInterpolation(local)
        val radius = RevealMath.lerp(22f * density, maxRadius, grow)
        val width = RevealMath.lerp(5f * density, 0.8f * density, grow)
        var a = if (local < 0.5f) 1f else 1f - (local - 0.5f) / 0.5f
        a = RevealMath.clamp(a, 0f, 1f) * 0.85f * gAlpha

        if (sweepCache[0] == null) {
            sweepCache[0] = SweepGradient(cx, cy, sweepLoop, null)
        }
        sweepMatrix.setRotate(grow * 180f, cx, cy)
        sweepCache[0]?.setLocalMatrix(sweepMatrix)
        waveStroke.shader = sweepCache[0]
        waveStroke.strokeWidth = width
        waveStroke.alpha = (255 * a).toInt()
        canvas.drawCircle(cx, cy, radius, waveStroke)
    }

    /**
     * Halftone dot rings. The Lite path drops the outer two rings
     * because they peak below 32 % alpha and the visual signal is
     * carried by the inner three.
     */
    fun drawHalftone(
        canvas: Canvas,
        dotPaint: Paint,
        dotGlow: Paint,
        sweepLoop: IntArray,
        brandBase: Int,
        cx: Float,
        cy: Float,
        density: Float,
        maxRadius: Float,
        fxLite: Boolean,
        local: Float,
        gAlpha: Float,
    ) {
        if (local <= 0f || local >= 1f) return

        val grow = EASE_OUT.getInterpolation(local)
        val radius = RevealMath.lerp(44f * density, maxRadius * 1.05f, grow)
        val band = 36f * density
        var a = if (local < 0.5f) 1f else 1f - (local - 0.5f) / 0.5f
        a = RevealMath.clamp(a, 0f, 1f) * gAlpha

        val ringCount = if (fxLite) 3 else 5
        val ringStep = band / 2f
        for (ring in 0 until ringCount) {
            val rOffset = (ring - (ringCount - 1) / 2f) * ringStep
            val r = radius + rOffset
            if (r <= 0f) continue

            val dots = minOf(64, maxOf(12, (r / (14f * density)).toInt()))
            val phase = ring * 0.37f + grow * 0.6f

            for (i in 0 until dots) {
                val angle = (2.0 * Math.PI * (i.toFloat() / dots) + phase).toFloat()
                val x = cx + cos(angle.toDouble()).toFloat() * r
                val y = cy + sin(angle.toDouble()).toFloat() * r

                val noise = ((i * 73 + ring * 19) % 100) / 100f
                val dotR = RevealMath.lerp(0.6f * density, 2.4f * density, noise)
                val colour = pickPaletteEntry(sweepLoop, brandBase, i + ring)

                val fall = 1f - minOf(1f, abs(rOffset) / band)
                val alpha = (255 * a * fall).toInt()
                if (alpha <= 4) continue

                dotGlow.color = RevealMath.withAlpha(colour, alpha / 2)
                dotPaint.color = RevealMath.withAlpha(colour, alpha)
                canvas.drawCircle(x, y, dotR * 1.8f, dotGlow)
                canvas.drawCircle(x, y, dotR, dotPaint)
            }
        }
    }

    /**
     * Build the rotated-square ("rhombus") spark path used by
     * [drawSpark]. Four straight segments connecting the cardinal
     * points (N → E → S → W → N). Lives here so callers can
     * rebuild on size change without touching host fields.
     *
     * The shape replaces the older four-pointed concave-sided
     * sparkle, which read as the well-known Google Material
     * "Sparkle" mark — an unintentional brand collision we wanted
     * to avoid. A plain rotated square ships the same chromatic
     * pop without resembling any third-party logo.
     */
    fun buildSparkPath(out: Path, cx: Float, cy: Float, r: Float) {
        out.reset()
        out.moveTo(cx, cy - r)
        out.lineTo(cx + r, cy)
        out.lineTo(cx, cy + r)
        out.lineTo(cx - r, cy)
        out.close()
    }

    /**
     * Pick one entry from the live brand-tinted sweep loop so
     * halftone / spark / decorator passes follow whatever palette
     * the sender icon produced via
     * [OtpRevealLayout.setBrandColor]. Indices wrap so negative
     * inputs still resolve cleanly.
     */
    fun pickPaletteEntry(sweepLoop: IntArray?, fallback: Int, i: Int): Int {
        val n = sweepLoop?.size ?: 0
        if (n <= 1 || sweepLoop == null) return fallback
        val useable = n - 1
        val idx = ((i % useable) + useable) % useable
        return sweepLoop[idx]
    }
}
