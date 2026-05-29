// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

import android.content.Context
import androidx.core.content.edit

/**
 * User-tunable overlay-effect knobs.
 *
 * Each visual layer of the reveal can be toggled or dialled
 * independently. Values are stored as 0..100 integers (intensity
 * sliders), raw integers (milliseconds, dp, mode ids), or booleans.
 * The defaults reproduce the design baseline; resetting every slider
 * therefore restores the canonical look.
 *
 * The public facade in [com.midtano.otp.data.Prefs] forwards every
 * method here, so external callers go through that single import.
 */
object PrefsFx {

    internal const val KEY_FX_PANEL_MUTE = "fx_panel_mute"
    internal const val KEY_FX_PERIM_EN = "fx_perim_en"
    internal const val KEY_FX_PERIM_INTEN = "fx_perim_inten"
    internal const val KEY_FX_PERIM_OUTER_EN = "fx_perim_outer_en"
    internal const val KEY_FX_PERIM_OUTER_W = "fx_perim_outer_w"
    internal const val KEY_FX_PERIM_OUTER_BL = "fx_perim_outer_bl"
    internal const val KEY_FX_PERIM_MID_W = "fx_perim_mid_w"
    internal const val KEY_FX_PERIM_MID_BL = "fx_perim_mid_bl"
    internal const val KEY_FX_PERIM_IN_W10 = "fx_perim_in_w10"
    internal const val KEY_FX_BREATH_EN = "fx_breath_en"
    internal const val KEY_FX_BREATH_AMT = "fx_breath_amt"
    internal const val KEY_FX_BREATH_PERIOD = "fx_breath_period"
    internal const val KEY_FX_ROT_EN = "fx_rot_en"
    internal const val KEY_FX_ROT_PERIOD = "fx_rot_period"
    internal const val KEY_FX_SWEEP_STOPS = "fx_sweep_stops"
    internal const val KEY_FX_SWEEP_HUE_RANGE = "fx_sweep_hue_range"
    internal const val KEY_FX_COUNTDOWN_STYLE = "fx_countdown_style"
    internal const val KEY_FX_HALO_EN = "fx_halo_en"
    internal const val KEY_FX_HALO_INTEN = "fx_halo_inten"
    internal const val KEY_FX_WAVE_EN = "fx_wave_en"
    internal const val KEY_FX_WAVE_INTEN = "fx_wave_inten"
    internal const val KEY_FX_HALFTONE_EN = "fx_halftone_en"
    internal const val KEY_FX_HALFTONE_INTEN = "fx_halftone_inten"
    internal const val KEY_FX_SPARK_EN = "fx_spark_en"
    internal const val KEY_FX_SPARK_INTEN = "fx_spark_inten"
    internal const val KEY_FX_COUNTDOWN_EN = "fx_countdown_en"
    internal const val KEY_FX_COUNTDOWN_INTEN = "fx_countdown_inten"
    internal const val KEY_FX_BLUR_EN = "fx_blur_en"
    internal const val KEY_FX_REVEAL_MS = "fx_reveal_ms"
    internal const val KEY_FX_DISMISS_MS = "fx_dismiss_ms"
    internal const val KEY_FX_EDGE_FADE = "fx_edge_fade"
    internal const val KEY_FX_COPY_LOTTIE_SPEED = "fx_copy_lottie_speed"
    internal const val KEY_FX_COPY_LOTTIE_EN = "fx_copy_lottie_en"
    internal const val KEY_FX_AUTOPASTE_LOTTIE_EN = "fx_autopaste_lottie_en"

    const val DEF_FX_PANEL_MUTE: Boolean = false
    const val DEF_FX_PERIM_EN: Boolean = true
    const val DEF_FX_PERIM_INTEN: Int = 100
    const val DEF_FX_PERIM_OUTER_EN: Boolean = true
    const val DEF_FX_PERIM_OUTER_W: Int = 14
    const val DEF_FX_PERIM_OUTER_BL: Int = 18
    const val DEF_FX_PERIM_MID_W: Int = 6
    const val DEF_FX_PERIM_MID_BL: Int = 6

    /**
     * Inner perimeter stroke width on disk, expressed as integer
     * tenths of a dp so a stock SeekBar can drive the slider with a
     * 0..50 range and one-tenth-dp steps. The pure-int storage
     * shape predates the typed accessors below.
     */
    const val DEF_FX_PERIM_IN_W10: Int = 10

    /**
     * Multiplier between [KEY_FX_PERIM_IN_W10] (0..50 SeekBar steps)
     * and the dp value the renderer needs (0..5f).
     */
    const val PERIM_IN_W_STEPS_PER_DP: Int = 10
    const val DEF_FX_BREATH_EN: Boolean = true
    const val DEF_FX_BREATH_AMT: Int = 100
    const val DEF_FX_BREATH_PERIOD: Int = 2000
    const val DEF_FX_ROT_EN: Boolean = true
    const val DEF_FX_ROT_PERIOD: Int = 9000

    const val DEF_FX_SWEEP_STOPS: Int = 3

    /** ±15° around the base hue. */
    const val DEF_FX_SWEEP_HUE_RANGE: Int = 30

    /**
     * Two countdown stroke styles, each with its own draw recipe.
     *
     * - [COUNTDOWN_SHRINK_BOTTOM] — symmetric shrink along the bottom edge.
     * - [COUNTDOWN_SWEEP_FULL]    — single segment retracting around the full perimeter.
     */
    const val COUNTDOWN_SHRINK_BOTTOM: Int = 0
    const val COUNTDOWN_SWEEP_FULL: Int = 1
    const val DEF_FX_COUNTDOWN_STYLE: Int = COUNTDOWN_SHRINK_BOTTOM

    const val DEF_FX_HALO_EN: Boolean = true
    const val DEF_FX_HALO_INTEN: Int = 100
    const val DEF_FX_WAVE_EN: Boolean = true
    const val DEF_FX_WAVE_INTEN: Int = 100
    const val DEF_FX_HALFTONE_EN: Boolean = true
    const val DEF_FX_HALFTONE_INTEN: Int = 100
    const val DEF_FX_SPARK_EN: Boolean = true
    const val DEF_FX_SPARK_INTEN: Int = 100
    const val DEF_FX_COUNTDOWN_EN: Boolean = true
    const val DEF_FX_COUNTDOWN_INTEN: Int = 100
    const val DEF_FX_BLUR_EN: Boolean = true
    const val DEF_FX_REVEAL_MS: Int = 1100
    const val DEF_FX_DISMISS_MS: Int = 240
    const val DEF_FX_EDGE_FADE: Int = 100

    /** Lottie copy-animation speed × 100 (150 = 1.5x). */
    const val DEF_FX_COPY_LOTTIE_SPEED: Int = 150
    const val DEF_FX_COPY_LOTTIE_EN: Boolean = true
    const val DEF_FX_AUTOPASTE_LOTTIE_EN: Boolean = true

    private fun getInt(c: Context, key: String, default: Int, lo: Int, hi: Int): Int =
        PrefsFile.clampI(PrefsFile.sp(c).getInt(key, default), lo, hi)

    private fun setInt(c: Context, key: String, v: Int, lo: Int, hi: Int) {
        PrefsFile.sp(c).edit { putInt(key, PrefsFile.clampI(v, lo, hi)) }
    }

    private fun getBool(c: Context, key: String, default: Boolean): Boolean =
        PrefsFile.sp(c).getBoolean(key, default)

    private fun setBool(c: Context, key: String, v: Boolean) {
        PrefsFile.sp(c).edit { putBoolean(key, v) }
    }

    fun isPanelMute(c: Context): Boolean = getBool(c, KEY_FX_PANEL_MUTE, DEF_FX_PANEL_MUTE)
    fun setPanelMute(c: Context, v: Boolean) = setBool(c, KEY_FX_PANEL_MUTE, v)

    fun isPerimEn(c: Context): Boolean = getBool(c, KEY_FX_PERIM_EN, DEF_FX_PERIM_EN)
    fun setPerimEn(c: Context, v: Boolean) = setBool(c, KEY_FX_PERIM_EN, v)
    fun getPerimInten(c: Context): Int = getInt(c, KEY_FX_PERIM_INTEN, DEF_FX_PERIM_INTEN, 0, 100)
    fun setPerimInten(c: Context, v: Int) = setInt(c, KEY_FX_PERIM_INTEN, v, 0, 100)

    fun isPerimOuterEn(c: Context): Boolean = getBool(c, KEY_FX_PERIM_OUTER_EN, DEF_FX_PERIM_OUTER_EN)
    fun setPerimOuterEn(c: Context, v: Boolean) = setBool(c, KEY_FX_PERIM_OUTER_EN, v)
    fun getPerimOuterW(c: Context): Int = getInt(c, KEY_FX_PERIM_OUTER_W, DEF_FX_PERIM_OUTER_W, 0, 40)
    fun setPerimOuterW(c: Context, v: Int) = setInt(c, KEY_FX_PERIM_OUTER_W, v, 0, 40)
    fun getPerimOuterBl(c: Context): Int = getInt(c, KEY_FX_PERIM_OUTER_BL, DEF_FX_PERIM_OUTER_BL, 0, 40)
    fun setPerimOuterBl(c: Context, v: Int) = setInt(c, KEY_FX_PERIM_OUTER_BL, v, 0, 40)

    fun getPerimMidW(c: Context): Int = getInt(c, KEY_FX_PERIM_MID_W, DEF_FX_PERIM_MID_W, 0, 20)
    fun setPerimMidW(c: Context, v: Int) = setInt(c, KEY_FX_PERIM_MID_W, v, 0, 20)
    fun getPerimMidBl(c: Context): Int = getInt(c, KEY_FX_PERIM_MID_BL, DEF_FX_PERIM_MID_BL, 0, 20)
    fun setPerimMidBl(c: Context, v: Int) = setInt(c, KEY_FX_PERIM_MID_BL, v, 0, 20)

    /**
     * Inner perimeter stroke width as **SeekBar steps** (0..50, one
     * step = one tenth of a dp). Use this from Settings sliders so
     * the value rounds the way the user moved the thumb.
     */
    fun getPerimInWidthSteps(c: Context): Int =
        getInt(c, KEY_FX_PERIM_IN_W10, DEF_FX_PERIM_IN_W10, 0, 50)

    /** Counterpart to [getPerimInWidthSteps]. */
    fun setPerimInWidthSteps(c: Context, v: Int) =
        setInt(c, KEY_FX_PERIM_IN_W10, v, 0, 50)

    /**
     * Inner perimeter stroke width as **dp** (0..5f). Use this from
     * the renderer so the unit at the call site matches the unit
     * the canvas paints in.
     */
    fun getPerimInWidthDp(c: Context): Float =
        getPerimInWidthSteps(c) / PERIM_IN_W_STEPS_PER_DP.toFloat()

    fun isBreathEn(c: Context): Boolean = getBool(c, KEY_FX_BREATH_EN, DEF_FX_BREATH_EN)
    fun setBreathEn(c: Context, v: Boolean) = setBool(c, KEY_FX_BREATH_EN, v)
    fun getBreathAmt(c: Context): Int = getInt(c, KEY_FX_BREATH_AMT, DEF_FX_BREATH_AMT, 0, 500)
    fun setBreathAmt(c: Context, v: Int) = setInt(c, KEY_FX_BREATH_AMT, v, 0, 500)
    fun getBreathPeriod(c: Context): Int = getInt(c, KEY_FX_BREATH_PERIOD, DEF_FX_BREATH_PERIOD, 500, 6000)
    fun setBreathPeriod(c: Context, v: Int) = setInt(c, KEY_FX_BREATH_PERIOD, v, 500, 6000)

    fun isRotEn(c: Context): Boolean = getBool(c, KEY_FX_ROT_EN, DEF_FX_ROT_EN)
    fun setRotEn(c: Context, v: Boolean) = setBool(c, KEY_FX_ROT_EN, v)
    fun getRotPeriod(c: Context): Int = getInt(c, KEY_FX_ROT_PERIOD, DEF_FX_ROT_PERIOD, 1000, 30000)
    fun setRotPeriod(c: Context, v: Int) = setInt(c, KEY_FX_ROT_PERIOD, v, 1000, 30000)

    fun getSweepStops(c: Context): Int = getInt(c, KEY_FX_SWEEP_STOPS, DEF_FX_SWEEP_STOPS, 1, 10)
    fun setSweepStops(c: Context, v: Int) = setInt(c, KEY_FX_SWEEP_STOPS, v, 1, 10)
    fun getSweepHueRange(c: Context): Int = getInt(c, KEY_FX_SWEEP_HUE_RANGE, DEF_FX_SWEEP_HUE_RANGE, 0, 180)
    fun setSweepHueRange(c: Context, v: Int) = setInt(c, KEY_FX_SWEEP_HUE_RANGE, v, 0, 180)

    fun getCountdownStyle(c: Context): Int = getInt(c, KEY_FX_COUNTDOWN_STYLE, DEF_FX_COUNTDOWN_STYLE, 0, 1)
    fun setCountdownStyle(c: Context, v: Int) = setInt(c, KEY_FX_COUNTDOWN_STYLE, v, 0, 1)

    fun isHaloEn(c: Context): Boolean = getBool(c, KEY_FX_HALO_EN, DEF_FX_HALO_EN)
    fun setHaloEn(c: Context, v: Boolean) = setBool(c, KEY_FX_HALO_EN, v)
    fun getHaloInten(c: Context): Int = getInt(c, KEY_FX_HALO_INTEN, DEF_FX_HALO_INTEN, 0, 100)
    fun setHaloInten(c: Context, v: Int) = setInt(c, KEY_FX_HALO_INTEN, v, 0, 100)

    fun isWaveEn(c: Context): Boolean = getBool(c, KEY_FX_WAVE_EN, DEF_FX_WAVE_EN)
    fun setWaveEn(c: Context, v: Boolean) = setBool(c, KEY_FX_WAVE_EN, v)
    fun getWaveInten(c: Context): Int = getInt(c, KEY_FX_WAVE_INTEN, DEF_FX_WAVE_INTEN, 0, 100)
    fun setWaveInten(c: Context, v: Int) = setInt(c, KEY_FX_WAVE_INTEN, v, 0, 100)

    fun isHalftoneEn(c: Context): Boolean = getBool(c, KEY_FX_HALFTONE_EN, DEF_FX_HALFTONE_EN)
    fun setHalftoneEn(c: Context, v: Boolean) = setBool(c, KEY_FX_HALFTONE_EN, v)
    fun getHalftoneInten(c: Context): Int = getInt(c, KEY_FX_HALFTONE_INTEN, DEF_FX_HALFTONE_INTEN, 0, 100)
    fun setHalftoneInten(c: Context, v: Int) = setInt(c, KEY_FX_HALFTONE_INTEN, v, 0, 100)

    fun isSparkEn(c: Context): Boolean = getBool(c, KEY_FX_SPARK_EN, DEF_FX_SPARK_EN)
    fun setSparkEn(c: Context, v: Boolean) = setBool(c, KEY_FX_SPARK_EN, v)
    fun getSparkInten(c: Context): Int = getInt(c, KEY_FX_SPARK_INTEN, DEF_FX_SPARK_INTEN, 0, 100)
    fun setSparkInten(c: Context, v: Int) = setInt(c, KEY_FX_SPARK_INTEN, v, 0, 100)

    fun isCountdownEn(c: Context): Boolean = getBool(c, KEY_FX_COUNTDOWN_EN, DEF_FX_COUNTDOWN_EN)
    fun setCountdownEn(c: Context, v: Boolean) = setBool(c, KEY_FX_COUNTDOWN_EN, v)
    fun getCountdownInten(c: Context): Int = getInt(c, KEY_FX_COUNTDOWN_INTEN, DEF_FX_COUNTDOWN_INTEN, 0, 100)
    fun setCountdownInten(c: Context, v: Int) = setInt(c, KEY_FX_COUNTDOWN_INTEN, v, 0, 100)

    fun isBlurEn(c: Context): Boolean = getBool(c, KEY_FX_BLUR_EN, DEF_FX_BLUR_EN)
    fun setBlurEn(c: Context, v: Boolean) = setBool(c, KEY_FX_BLUR_EN, v)

    fun getRevealMs(c: Context): Int = getInt(c, KEY_FX_REVEAL_MS, DEF_FX_REVEAL_MS, 200, 3000)
    fun setRevealMs(c: Context, v: Int) = setInt(c, KEY_FX_REVEAL_MS, v, 200, 3000)
    fun getDismissMs(c: Context): Int = getInt(c, KEY_FX_DISMISS_MS, DEF_FX_DISMISS_MS, 50, 1000)
    fun setDismissMs(c: Context, v: Int) = setInt(c, KEY_FX_DISMISS_MS, v, 50, 1000)

    fun getEdgeFade(c: Context): Int = getInt(c, KEY_FX_EDGE_FADE, DEF_FX_EDGE_FADE, 0, 100)
    fun setEdgeFade(c: Context, v: Int) = setInt(c, KEY_FX_EDGE_FADE, v, 0, 100)

    /** Lottie copy-animation speed stored as percent: 50..300 → 0.5x..3.0x. */
    fun getCopyLottieSpeed(c: Context): Int = getInt(c, KEY_FX_COPY_LOTTIE_SPEED, DEF_FX_COPY_LOTTIE_SPEED, 50, 300)
    fun setCopyLottieSpeed(c: Context, v: Int) = setInt(c, KEY_FX_COPY_LOTTIE_SPEED, v, 50, 300)
    fun getCopyLottieSpeedFloat(c: Context): Float = getCopyLottieSpeed(c) / 100f

    fun isCopyLottieEn(c: Context): Boolean = getBool(c, KEY_FX_COPY_LOTTIE_EN, DEF_FX_COPY_LOTTIE_EN)
    fun setCopyLottieEn(c: Context, v: Boolean) = setBool(c, KEY_FX_COPY_LOTTIE_EN, v)

    fun isAutopasteLottieEn(c: Context): Boolean = getBool(c, KEY_FX_AUTOPASTE_LOTTIE_EN, DEF_FX_AUTOPASTE_LOTTIE_EN)
    fun setAutopasteLottieEn(c: Context, v: Boolean) = setBool(c, KEY_FX_AUTOPASTE_LOTTIE_EN, v)
}
