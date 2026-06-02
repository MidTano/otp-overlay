 // SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.palette.graphics.Palette
import com.midtano.otp.R
import com.midtano.otp.service.OverlayService
import java.util.Locale
import kotlin.math.abs

/**
 * Sender-app icon resolution and brand-colour extraction for the
 * OTP overlay header.
 */
internal object AppIconResolver {

    /**
     * Rotating colour palette used for the test overlay and for any
     * synthetic sender avatar. With a `null` seed we cycle by wall
     * clock so repeated test taps walk through obviously different
     * hues; with a sender name we hash it so the same sender keeps
     * the same colour across cards.
     */
    private val TEST_PALETTE: IntArray = intArrayOf(
        0xFF34C759.toInt(),
        0xFF0A84FF.toInt(),
        0xFFFF9F0A.toInt(),
        0xFFFF375F.toInt(),
        0xFFAF52DE.toInt(),
        0xFF5AC8FA.toInt(),
        0xFFFFCC00.toInt(),
        0xFF30D158.toInt(),
    )

    /** Returns the launcher icon for the given package, or `null` on miss. */
    fun resolveAppIcon(ctx: Context, pkg: String?): Drawable? {
        if (pkg.isNullOrEmpty()) return null
        return try {
            ctx.packageManager.getApplicationIcon(pkg)
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Pick a colour from [TEST_PALETTE]. A `null` seed cycles by
     * wall clock; a non-null seed hashes deterministically.
     */
    fun pickTestPalette(seed: String?): Int {
        if (seed.isNullOrEmpty()) {
            return TEST_PALETTE[((System.currentTimeMillis() / 600L) % TEST_PALETTE.size).toInt()]
        }
        var h = 0
        for (c in seed) h = h * 31 + c.code
        return TEST_PALETTE[abs(h) % TEST_PALETTE.size]
    }

    /**
     * Build a coloured circular icon with the sender's first letter
     * for the test overlay so the user can preview the
     * dynamic-theme behaviour even when no real launcher icon is
     * available.
     */
    fun makeSyntheticAppIcon(res: Resources, sender: String?, brand: Int): Drawable {
        val size = (44 * res.displayMetrics.density).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (brand != 0) brand else ResourcesCompat.getColor(res, R.color.app_icon_fallback_bg, null)
        }
        c.drawCircle(size * 0.5f, size * 0.5f, size * 0.48f, pBg)
        val letter = if (!sender.isNullOrEmpty()) {
            sender.trim().substring(0, 1).uppercase(Locale.ROOT)
        } else {
            "?"
        }
        val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ResourcesCompat.getColor(res, R.color.app_icon_fallback_text, null)
            textSize = size * 0.56f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val fm = pTxt.fontMetrics
        val baseline = size * 0.5f - (fm.ascent + fm.descent) * 0.5f
        c.drawText(letter, size * 0.5f, baseline, pTxt)
        return bmp.toDrawable(res)
    }

    /**
     * Compute a single representative brand colour for the
     * overlay's glow / stroke from the sender app icon. Returns `0`
     * for unknown / greyscale icons; callers interpret that as
     * "keep the default indigo palette".
     *
     * The synthetic test source has no real icon, so it gets a
     * deterministic palette pick instead.
     */
    fun dominantColor(icon: Drawable?, source: String?): Int {
        if (OverlayService.SOURCE_TEST == source) {
            return pickTestPalette(null)
        }
        if (icon == null) return 0
        return try {
            var w = maxOf(8, minOf(64, icon.intrinsicWidth))
            var h = maxOf(8, minOf(64, icon.intrinsicHeight))
            if (w <= 0 || h <= 0) {
                w = 48
                h = 48
            }
            val bmp = createBitmap(w, h)
            val c = Canvas(bmp)
            icon.setBounds(0, 0, w, h)
            icon.draw(c)
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            var color = p.getVibrantColor(0)
            if (color == 0) color = p.getMutedColor(0)
            if (color == 0) color = p.getDominantColor(0)
            bmp.recycle()
            // Reject near-greyscale results so a black/white icon
            // does not strip the colour out of the glow.
            if (color != 0) {
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                if (hsv[1] < 0.18f) return 0
            }
            color
        } catch (_: IllegalArgumentException) {
            // createBitmap rejects zero / negative dimensions on
            // some pathological adaptive icons; Palette.from rejects
            // a recycled bitmap on the same code path. Both surface
            // as IllegalArgumentException; falling back to 0 means
            // the caller paints a neutral glow.
            0
        }
    }
}
