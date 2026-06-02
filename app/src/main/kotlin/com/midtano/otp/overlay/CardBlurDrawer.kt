// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave

/**
 * Frosted-glass blur applied to the OTP card after the user copies
 * the code.
 *
 * Owns the blur snapshot bitmap lifecycle, the [RenderEffect]-based
 * hardware path, the software fallback, and the per-frame composite
 * (round-rect clip + 9-tap progressive blur + gray wash).
 *
 * Lifecycle:
 * 1. [apply] — called once when the user copies. Either installs a
 *    [RenderEffect] on the card view (the hardware path) or captures
 *    a downscaled bitmap snapshot (the software fallback for OEM
 *    builds that lie about supporting [RenderEffect]).
 * 2. [drawOverlay] — called from the host's `dispatchDraw` every
 *    frame while the blur is visible.
 * 3. [recycle] — called when the card is detached.
 */
internal class CardBlurDrawer {

    /**
     * `true` once the blur has been applied. Never reset; the blur
     * stays on for the rest of the card's lifetime.
     */
    var isApplied: Boolean = false
        private set

    /** Cached downscaled card snapshot used by the software fallback. */
    private var snapshot: Bitmap? = null

    /**
     * Apply the blur to [card].
     *
     * @param card    the OTP card view
     * @param enabled user preference — when `false` we just latch
     *                [isApplied] so callers don't keep retrying and
     *                we skip the per-frame composite.
     */
    fun apply(card: View?, enabled: Boolean) {
        if (isApplied) return
        if (!enabled) {
            isApplied = true
            return
        }
        if (card == null) return
        val w = card.width
        val h = card.height
        if (w <= 0 || h <= 0) {
            isApplied = true
            return
        }

        // Hardware path: GPU blur in a single composite pass.
        var hwApplied = false
        try {
            val blur = RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
            card.setRenderEffect(blur)
            // Drop any prior snapshot — the software fallback bitmap
            // is no longer needed.
            snapshot = null
            hwApplied = true
        } catch (_: Throwable) {
            // Some OEM builds advertise API 31 but ship without
            // RenderEffect support; fall through to the software path.
        }

        if (!hwApplied) {
            try {
                val scale = 12
                val dw = maxOf(1, w / scale)
                val dh = maxOf(1, h / scale)
                val bm = createBitmap(dw, dh)
                val c = Canvas(bm)
                c.scale(1f / scale, 1f / scale)
                card.draw(c)
                snapshot = bm
            } catch (_: Throwable) {
                snapshot = null
            }
        }
        isApplied = true
    }

    /**
     * Clear any [RenderEffect] attached during [apply] and recycle
     * the snapshot bitmap. Called from the host's
     * `onDetachedFromWindow` so a re-used card view does not inherit
     * a stale blur from a previous reveal.
     */
    fun recycle() {
        val bm = snapshot
        snapshot = null
        isApplied = false
        if (bm != null) {
            try { bm.recycle() } catch (_: Throwable) {}
        }
    }

    /**
     * Composite the blur onto [canvas] respecting the card's rounded
     * corners and the host's per-frame `blurAlpha` / `effectsAlpha`.
     *
     * @param canvas       the host's `dispatchDraw` canvas
     * @param card         the live card view (provides layout coords + alpha)
     * @param density      pixel density for dp conversion
     * @param blurAlpha    growth factor 0..1 across the reveal
     * @param effectsAlpha global effects alpha (used for dismissal fade)
     */
    fun drawOverlay(
        canvas: Canvas,
        card: View?,
        density: Float,
        blurAlpha: Float,
        effectsAlpha: Float,
    ) {
        if (blurAlpha <= 0f) return
        if (card == null) return
        val cardLeft = card.left
        val cardTop = card.top
        val cardW = card.width
        val cardH = card.height
        if (cardW <= 0 || cardH <= 0) return
        val cardAlpha = card.alpha
        val composite = clamp(cardAlpha * blurAlpha, 0f, 1f) * clamp(effectsAlpha, 0f, 1f)
        if (composite <= 0f) return
        val dst = RectF(
            cardLeft.toFloat(),
            cardTop.toFloat(),
            (cardLeft + cardW).toFloat(),
            (cardTop + cardH).toFloat(),
        )
        val dp12 = 12f * density

        // Hardware-blur path: RenderEffect already softened the card
        // content. Only a soft gray wash is needed so the OTP text
        // underneath reads as a frosted pane.
        if (snapshot == null) {
            val clip = Path().apply { addRoundRect(dst, dp12, dp12, Path.Direction.CW) }
            canvas.withSave {
                clipPath(clip)
                val wash = Paint().apply {
                    color = 0x60111111
                    alpha = (96 * composite).toInt()
                }
                drawRect(dst, wash)
            }
            return
        }

        // Software fallback: 9-tap upscale composite from the snapshot.
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Round-rect clip so the blur respects the card's rounded
        // corners.
        val clip = Path().apply { addRoundRect(dst, dp12, dp12, Path.Direction.CW) }
        canvas.withSave {
            clipPath(clip)

        // Progressive blur: the box-blur tap radius grows with
        // blurAlpha so the blur intensity actually animates across
        // the reveal — early frames are nearly identity, late frames
        // are full frosted glass.
        val radiusMul = 0.25f + 0.75f * blurAlpha
        val taps = arrayOf(
            floatArrayOf(0f, 0f, 0.30f),
            floatArrayOf(2f, 2f, 0.18f),
            floatArrayOf(-2f, 2f, 0.18f),
            floatArrayOf(2f, -2f, 0.14f),
            floatArrayOf(-2f, -2f, 0.14f),
            floatArrayOf(3.5f, 0f, 0.10f),
            floatArrayOf(-3.5f, 0f, 0.10f),
            floatArrayOf(0f, 3.5f, 0.10f),
            floatArrayOf(0f, -3.5f, 0.10f),
        )
        // Slight scale-up of the blurred bitmap (1.00 → 1.025) gives a
        // gentle "lens focusing in" feel.
        val scale = 1f + 0.025f * blurAlpha
        val scaleDx = (cardW * (scale - 1f)) * 0.5f
        val scaleDy = (cardH * (scale - 1f)) * 0.5f
        val scaledDst = RectF(
            dst.left - scaleDx,
            dst.top - scaleDy,
            dst.right + scaleDx,
            dst.bottom + scaleDy,
        )
        val bm = snapshot
        if (bm != null) {
            for (t in taps) {
                p.alpha = (255 * t[2] * composite).toInt()
                val od = RectF(scaledDst)
                od.offset(t[0] * density * radiusMul, t[1] * density * radiusMul)
                drawBitmap(bm, null, od, p)
            }
        }

        // Soft gray wash flattens any residual silhouette of the OTP
        // text. Mirrors the card background colour so the user sees a
        // clean frosted gray pane.
        val wash = Paint().apply {
            color = 0x60111111
            alpha = (96 * composite).toInt()
        }
        drawRect(dst, wash)

        // Faint cool highlight at the top of the card during the
        // reveal — suggests a frosted-glass surface catching light.
        val highlightT = if (blurAlpha < 0.5f) {
            blurAlpha / 0.5f
        } else {
            1f - (blurAlpha - 0.5f) / 0.5f
        }
        if (highlightT > 0f) {
            val hl = Paint(Paint.ANTI_ALIAS_FLAG)
            val hlAlpha = (36 * highlightT * composite).toInt()
            hl.shader = LinearGradient(
                dst.left, dst.top, dst.left, dst.top + cardH * 0.55f,
                (hlAlpha shl 24) or 0xFFFFFF,
                0x00FFFFFF,
                Shader.TileMode.CLAMP,
            )
            drawRect(dst, hl)
            hl.shader = null
        }
        }
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float = when {
        v < lo -> lo
        v > hi -> hi
        else -> v
    }

    companion object {
        /**
         * Clear the [RenderEffect] installed on a card view. Separate
         * from [recycle] because that method does not know which card
         * the drawer was last attached to.
         */
        fun clearRenderEffect(card: View?) {
            if (card == null) return
            try { card.setRenderEffect(null) } catch (_: Throwable) {}
        }
    }
}
