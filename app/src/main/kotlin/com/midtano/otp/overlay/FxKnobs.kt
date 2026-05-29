// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import android.content.Context
import com.midtano.otp.data.Prefs
import com.midtano.otp.system.CrashLogger

/**
 * Snapshot of every overlay-effect knob the user can adjust in
 * Settings. Read once on attach and refreshed whenever the prefs-
 * change listener fires.
 *
 * Mutable POJO by design: [OtpRevealLayout] updates the same
 * instance in place rather than allocating a fresh snapshot per
 * prefs-change tick. Defaults match the in-app baseline so a freshly
 * inflated overlay works even before the first [loadFrom] call.
 */
internal class FxKnobs {
    var panelMute: Boolean = true
    var perimEn: Boolean = true
    var perimInten: Float = 1f // 0..1
    var perimOuterEn: Boolean = true
    var perimOuterW: Float = 14f // dp
    var perimOuterBl: Float = 18f // dp blur
    var perimMidW: Float = 6f // dp
    var perimMidBl: Float = 6f // dp blur
    var perimInW: Float = 1.0f // dp
    var breathEn: Boolean = true
    var breathAmt: Float = 1f // 0..1
    var breathPeriod: Long = 2000L
    var rotEn: Boolean = true
    var rotPeriod: Long = 9000L
    var sweepStops: Int = 3 // 1..10
    var sweepHueRange: Float = 30f // degrees
    var haloEn: Boolean = true
    var haloInten: Float = 1f
    var waveEn: Boolean = true
    var waveInten: Float = 1f
    var halftoneEn: Boolean = true
    var halftoneInten: Float = 1f
    var sparkEn: Boolean = true
    var sparkInten: Float = 1f
    var countdownEn: Boolean = true
    var countdownInten: Float = 1f
    var countdownStyle: Int = 0
    var blurEn: Boolean = true
    var revealMs: Long = 1100L
    var dismissMs: Long = 240L
    var edgeFade: Float = 1f // 0..1

    /**
     * Refresh every field from [Prefs]. Returns `true` if any
     * sweep-affecting knob changed (stop count or hue range), so the
     * caller can decide whether to rebuild cached gradients.
     *
     * Each prefs read is wrapped in a try/catch so a single failing
     * lookup (e.g. a `clearAll` race) does not abort the whole
     * refresh.
     */
    fun loadFrom(ctx: Context): Boolean {
        val prevSweepStops = sweepStops
        val prevSweepRange = sweepHueRange
        try {
            panelMute = Prefs.isFxPanelMute(ctx)
            perimEn = Prefs.isFxPerimEn(ctx)
            perimInten = Prefs.getFxPerimInten(ctx) / 100f
            perimOuterEn = Prefs.isFxPerimOuterEn(ctx)
            perimOuterW = Prefs.getFxPerimOuterW(ctx).toFloat()
            perimOuterBl = Prefs.getFxPerimOuterBl(ctx).toFloat()
            perimMidW = Prefs.getFxPerimMidW(ctx).toFloat()
            perimMidBl = Prefs.getFxPerimMidBl(ctx).toFloat()
            perimInW = Prefs.getFxPerimInWidthDp(ctx)
            breathEn = Prefs.isFxBreathEn(ctx)
            breathAmt = Prefs.getFxBreathAmt(ctx) / 100f
            breathPeriod = Prefs.getFxBreathPeriod(ctx).toLong()
            rotEn = Prefs.isFxRotEn(ctx)
            rotPeriod = Prefs.getFxRotPeriod(ctx).toLong()
            sweepStops = Prefs.getFxSweepStops(ctx)
            sweepHueRange = Prefs.getFxSweepHueRange(ctx).toFloat()
            haloEn = Prefs.isFxHaloEn(ctx)
            haloInten = Prefs.getFxHaloInten(ctx) / 100f
            waveEn = Prefs.isFxWaveEn(ctx)
            waveInten = Prefs.getFxWaveInten(ctx) / 100f
            halftoneEn = Prefs.isFxHalftoneEn(ctx)
            halftoneInten = Prefs.getFxHalftoneInten(ctx) / 100f
            sparkEn = Prefs.isFxSparkEn(ctx)
            sparkInten = Prefs.getFxSparkInten(ctx) / 100f
            countdownEn = Prefs.isFxCountdownEn(ctx)
            countdownInten = Prefs.getFxCountdownInten(ctx) / 100f
            countdownStyle = Prefs.getFxCountdownStyle(ctx)
            blurEn = Prefs.isFxBlurEn(ctx)
            revealMs = Prefs.getFxRevealMs(ctx).toLong()
            dismissMs = Prefs.getFxDismissMs(ctx).toLong()
            edgeFade = Prefs.getFxEdgeFade(ctx) / 100f
        } catch (e: Exception) {
            CrashLogger.logErr("FxKnobs.loadFrom failed", e)
        }
        return prevSweepStops != sweepStops || prevSweepRange != sweepHueRange
    }
}
