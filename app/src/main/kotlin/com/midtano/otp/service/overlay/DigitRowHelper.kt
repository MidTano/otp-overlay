// SPDX-License-Identifier: MIT
//
// DisplayMetrics.scaledDensity — the typed replacement still needs
// DisplayMetrics, so the read here is unavoidable.
@file:Suppress("DEPRECATION")

package com.midtano.otp.service.overlay

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isNotEmpty
import com.midtano.otp.R
import com.midtano.otp.system.CrashLogger

/** Builds and animates the OTP digit row inside the overlay card. */
internal object DigitRowHelper {

    const val DIGIT_TEXT_SP_MAX: Float = 20f
    const val DIGIT_TEXT_SP_MIN: Float = 13f

    fun populateDigitsAnimated(
        ctx: Context,
        handler: Handler,
        container: LinearLayout?,
        otp: String?,
    ) {
        if (container == null || otp == null) return
        container.removeAllViews()

        val jbm: Typeface = try {
            ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_bold) ?: Typeface.MONOSPACE
        } catch (_: android.content.res.Resources.NotFoundException) {
            Typeface.MONOSPACE
        }

        val len = otp.length
        for (i in 0 until len) {
            val tv = TextView(ctx).apply {
                text = otp[i].toString()
                setTextColor(ContextCompat.getColor(ctx, R.color.digit_text))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, DIGIT_TEXT_SP_MAX)
                includeFontPadding = false
                typeface = jbm
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = if (i == 0) 0 else dp(ctx, 1.5f) }
            tv.layoutParams = lp
            tv.alpha = 0f
            tv.translationY = dp(ctx, 8f).toFloat()
            container.addView(tv)

            val ref = tv
            val idx = i
            handler.postDelayed({
                try {
                    ref.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(392)
                        .setInterpolator(DecelerateInterpolator(1.6f))
                        .start()
                } catch (e: IllegalStateException) {
                    // ViewPropertyAnimator throws IllegalStateException
                    // when the View is mid-detach — rare race when
                    // the overlay is torn down right at digit-row
                    // arrival. Cosmetic, non-fatal.
                    CrashLogger.logErr("DigitRowHelper: digit reveal animate threw", e)
                }
            }, 84L + idx * 77L)
        }
    }

    /**
     * Estimate the digit row's natural width without laying out the
     * actual TextViews (which are not yet attached to the parent
     * when this is called). Mirrors the populator's font + size +
     * per-digit margin so the sender → icon tuck animation can
     * sequence correctly.
     */
    fun estimateDigitRowWidth(ctx: Context, otp: String?): Int {
        if (otp.isNullOrEmpty()) return 0
        val jbm: Typeface = try {
            ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_bold) ?: Typeface.MONOSPACE
        } catch (_: android.content.res.Resources.NotFoundException) {
            Typeface.MONOSPACE
        }
        val probe = TextView(ctx).apply {
            text = otp
            setTextSize(TypedValue.COMPLEX_UNIT_SP, DIGIT_TEXT_SP_MAX)
            includeFontPadding = false
            typeface = jbm
        }
        val wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        probe.measure(wSpec, hSpec)
        // Add the same per-digit margin the populator inserts.
        val extra = dp(ctx, 1.5f) * (otp.length - 1)
        return probe.measuredWidth + extra
    }

    /**
     * Re-measure the digit row's natural width with an unbounded
     * spec. Children are already laid out, but their `measuredWidth`
     * reflects the post-clip parent — we need the "what would I
     * want with infinite space" answer to decide whether to shrink.
     */
    fun measureDigitRowWidth(digits: LinearLayout?): Int {
        if (digits == null) return 0
        val wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        digits.measure(wSpec, hSpec)
        return digits.measuredWidth
    }

    /** Restore the per-digit text size to the design default. */
    fun resetDigitTextSize(ctx: Context, digits: LinearLayout?) {
        if (digits == null) return
        for (i in 0 until digits.childCount) {
            val c = digits.getChildAt(i)
            if (c is TextView) {
                val curSp = c.textSize / ctx.resources.displayMetrics.scaledDensity
                if (curSp < DIGIT_TEXT_SP_MAX - 0.5f) {
                    c.setTextSize(TypedValue.COMPLEX_UNIT_SP, DIGIT_TEXT_SP_MAX)
                }
            }
        }
    }

    /**
     * Shrink the digit-row text size in 1 sp steps until the row
     * fits inside [maxWidth] or hits [DIGIT_TEXT_SP_MIN].
     */
    fun shrinkDigitsToFit(ctx: Context, digits: LinearLayout?, maxWidth: Int) {
        if (digits == null || maxWidth <= 0) return
        val scaledDensity = ctx.resources.displayMetrics.scaledDensity
        val first = if (digits.isNotEmpty()) digits.getChildAt(0) else null
        var sp = if (first is TextView) first.textSize / scaledDensity else DIGIT_TEXT_SP_MAX
        while (sp >= DIGIT_TEXT_SP_MIN) {
            for (i in 0 until digits.childCount) {
                val c = digits.getChildAt(i)
                if (c is TextView) c.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            }
            val needed = measureDigitRowWidth(digits)
            if (needed <= maxWidth) return
            sp -= 1f
        }
    }

    private fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
