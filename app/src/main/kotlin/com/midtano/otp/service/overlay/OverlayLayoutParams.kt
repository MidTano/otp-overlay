// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

/**
 * [WindowManager.LayoutParams] factories for the OTP card window
 * and the auto-paste toast pill.
 */
internal object OverlayLayoutParams {

    /**
     * OTP card window.
     *
     * Width is locked to the device's portrait short-edge in px
     * rather than [WindowManager.LayoutParams.MATCH_PARENT]. In
     * landscape, MATCH_PARENT stretches the card across the long
     * edge, which makes the OTP read awkwardly wide. Pinning the
     * width to `min(widthPx, heightPx)` keeps the card the same
     * physical size in both orientations — the same look as in
     * portrait, centred horizontally.
     *
     * - `FLAG_ALT_FOCUSABLE_IM` lets us receive key events (so BACK
     *   works) without yanking IME focus from the underlying app.
     * - `FLAG_HARDWARE_ACCELERATED` is critical on OEMs that
     *   otherwise render `TYPE_APPLICATION_OVERLAY` windows on a
     *   software canvas — that path stutters when the shade is
     *   pulled.
     */
    fun buildCardParams(ctx: Context): WindowManager.LayoutParams {
        val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        return WindowManager.LayoutParams(
            portraitCardWidthPx(ctx),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
    }

    /**
     * Width the OTP card should occupy in pixels — the short edge
     * of the display, so the card looks the same size in portrait
     * and landscape. Used by both the initial card attach and the
     * orientation-change relayout in `OverlayService`.
     */
    fun portraitCardWidthPx(ctx: Context): Int {
        val dm = ctx.resources.displayMetrics
        return minOf(dm.widthPixels, dm.heightPixels)
    }

    /**
     * Compact window params for the auto-paste toast pill. Width is
     * `WRAP_CONTENT` so the pill sizes to its content (icon + label
     * + digits) instead of stretching the whole screen. The 8 dp
     * top inset prevents the pill from sitting flush against the
     * status bar.
     */
    fun buildToastParams(ctx: Context): WindowManager.LayoutParams {
        val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (8f * ctx.resources.displayMetrics.density).toInt()
        }
    }
}
