// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import com.midtano.otp.data.Prefs

/**
 * Paints the countdown stroke around the OTP card. Two visual
 * styles dispatched on [Prefs.getFxCountdownStyle]:
 *
 * - [Prefs.COUNTDOWN_SHRINK_BOTTOM] — symmetric shrink along the
 *   bottom edge.
 * - [Prefs.COUNTDOWN_SWEEP_FULL]    — single segment retracting
 *   around the full perimeter.
 *
 * Pure draw helper — owns nothing but its scratch [Path]s and the
 * [Paint]s passed in by the host.
 */
internal class CountdownDrawer {

    private val scratchPath = Path()
    private val scratchSeg = Path()

    /**
     * Dispatch on the user-selected countdown style.
     *
     * @param canvas      canvas of the host `dispatchDraw`
     * @param card        live card view (provides geometry)
     * @param glow        pre-allocated stroke paint owned by the host
     * @param core        pre-allocated bright core paint
     * @param density     pixel density for dp conversion
     * @param countdownT  1 = full time remaining, 0 = expired
     * @param gAlpha      global alpha (effects fade)
     * @param style       one of `Prefs.COUNTDOWN_*`
     */
    fun draw(
        canvas: Canvas,
        card: View?,
        glow: Paint,
        core: Paint,
        density: Float,
        countdownT: Float,
        gAlpha: Float,
        style: Int,
    ) {
        if (card == null) return
        when (style) {
            Prefs.COUNTDOWN_SWEEP_FULL -> drawSweepFull(canvas, card, glow, core, density, countdownT, gAlpha)
            else -> drawShrinkBottom(canvas, card, glow, core, density, countdownT, gAlpha)
        }
    }

    /** Full perimeter path centred on bottom-centre. Shared by both styles. */
    private fun buildPerimeterFromBottomCenter(card: View, density: Float, out: Path) {
        out.reset()
        val left = card.left.toFloat()
        val top = card.top.toFloat()
        val right = card.right.toFloat()
        val bottom = card.bottom.toFloat()
        val corner = 12f * density
        val cxBot = (left + right) * 0.5f
        out.moveTo(cxBot, bottom)
        out.lineTo(right - corner, bottom)
        out.arcTo(right - 2 * corner, bottom - 2 * corner, right, bottom, 90f, -90f, false)
        out.lineTo(right, top + corner)
        out.arcTo(right - 2 * corner, top, right, top + 2 * corner, 0f, -90f, false)
        out.lineTo(left + corner, top)
        out.arcTo(left, top, left + 2 * corner, top + 2 * corner, 270f, -90f, false)
        out.lineTo(left, bottom - corner)
        out.arcTo(left, bottom - 2 * corner, left + 2 * corner, bottom, 180f, -90f, false)
        out.lineTo(cxBot, bottom)
    }

    /** Bottom-only path used by SHRINK_BOTTOM. */
    private fun buildBottomCurvePath(card: View, density: Float, out: Path) {
        out.reset()
        val left = card.left.toFloat()
        val right = card.right.toFloat()
        val bottom = card.bottom.toFloat()
        val corner = 12f * density
        out.moveTo(left, bottom - corner)
        out.arcTo(left, bottom - 2 * corner, left + 2 * corner, bottom, 180f, -90f, false)
        out.lineTo(right - corner, bottom)
        out.arcTo(right - 2 * corner, bottom - 2 * corner, right, bottom, 90f, -90f, false)
    }

    /** SHRINK_BOTTOM — symmetric shrink toward bottom-centre on the bottom edge. */
    private fun drawShrinkBottom(
        canvas: Canvas,
        card: View,
        glow: Paint,
        core: Paint,
        density: Float,
        countdownT: Float,
        gAlpha: Float,
    ) {
        buildBottomCurvePath(card, density, scratchPath)
        val pm = PathMeasure(scratchPath, false)
        val total = pm.length
        val visible = total * countdownT
        val startD = (total - visible) / 2f
        val endD = startD + visible
        scratchSeg.reset()
        pm.getSegment(startD, endD, scratchSeg, true)
        paintWhiteStroke(canvas, scratchSeg, glow, core, density, gAlpha)
    }

    /**
     * SWEEP_FULL — single segment around the full perimeter. The
     * trailing end retracts toward bottom-centre as time runs down,
     * so the user reads the line as draining.
     */
    private fun drawSweepFull(
        canvas: Canvas,
        card: View,
        glow: Paint,
        core: Paint,
        density: Float,
        countdownT: Float,
        gAlpha: Float,
    ) {
        buildPerimeterFromBottomCenter(card, density, scratchPath)
        val pm = PathMeasure(scratchPath, false)
        val total = pm.length
        val visible = total * countdownT
        scratchSeg.reset()
        pm.getSegment(0f, visible, scratchSeg, true)
        paintWhiteStroke(canvas, scratchSeg, glow, core, density, gAlpha)
    }

    /** Common single white stroke painter used by both styles. */
    private fun paintWhiteStroke(
        canvas: Canvas,
        seg: Path,
        glow: Paint,
        core: Paint,
        density: Float,
        gAlpha: Float,
    ) {
        glow.shader = null
        glow.color = RevealPalette.COUNTDOWN_WHITE
        glow.strokeWidth = 3f * density
        glow.alpha = (90 * gAlpha).toInt()
        canvas.drawPath(seg, glow)
        core.shader = null
        core.color = RevealPalette.COUNTDOWN_WHITE
        core.strokeWidth = 1.4f * density
        core.alpha = (255 * gAlpha).toInt()
        canvas.drawPath(seg, core)
    }
}
