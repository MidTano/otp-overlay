// SPDX-License-Identifier: MIT
package com.midtano.otp.data

import android.content.Context
import android.content.SharedPreferences
import com.midtano.otp.data.prefs.DisplayMode
import com.midtano.otp.data.prefs.FxLevel
import com.midtano.otp.data.prefs.PrefsCore
import com.midtano.otp.data.prefs.PrefsFile
import com.midtano.otp.data.prefs.PrefsFilter
import com.midtano.otp.data.prefs.PrefsFx
import com.midtano.otp.data.prefs.PrefsFxLevel
import com.midtano.otp.data.prefs.PrefsLocale
import com.midtano.otp.locale.AppLanguage

/**
 * Public preference facade routing every call site through a single
 * import.
 *
 * Thin wrapper over the typed helpers in [com.midtano.otp.data.prefs]:
 * - [PrefsCore]    — back-tap, auto-paste, sounds, onboarding, shade.
 * - [PrefsFx]      — overlay-effect knobs.
 * - [PrefsFxLevel] — FULL / LITE / ULTRA preset.
 * - [PrefsLocale]  — language preference.
 * - [PrefsFilter]  — allowed apps, regex, trigger / stop / ignore /
 *   cleanup / currency phrase lists.
 *
 * Forwarding constants are re-exported here so call sites that read
 * them via `Prefs.*` keep working when the storage layout evolves.
 *
 * Phrase-list reads return immutable `List<String>`. To mutate, build
 * a fresh list (`current + value`, `current.filterNot { ... }`) and
 * pass it to the matching setter.
 */
object Prefs {

    const val COUNTDOWN_SHRINK_BOTTOM: Int = PrefsFx.COUNTDOWN_SHRINK_BOTTOM
    const val COUNTDOWN_SWEEP_FULL: Int = PrefsFx.COUNTDOWN_SWEEP_FULL

    const val SHADE_DURATION_MIN_MS: Int = PrefsCore.SHADE_DURATION_MIN_MS
    const val SHADE_DURATION_MAX_MS: Int = PrefsCore.SHADE_DURATION_MAX_MS

    /**
     * Subscribe to live preference edits so an attached overlay can
     * refresh its FX knobs the moment a slider in Settings moves.
     */
    fun registerChangeListener(
        ctx: Context?,
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (ctx == null || listener == null) return
        PrefsFile.sp(ctx).registerOnSharedPreferenceChangeListener(listener)
    }

    /** Counterpart to [registerChangeListener]. Idempotent. */
    fun unregisterChangeListener(
        ctx: Context?,
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (ctx == null || listener == null) return
        PrefsFile.sp(ctx).unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isBackCopy(ctx: Context): Boolean = PrefsCore.isBackCopy(ctx)
    fun isAutoPaste(ctx: Context): Boolean = PrefsCore.isAutoPaste(ctx)
    fun isAutopasteNoCopy(ctx: Context): Boolean = PrefsCore.isAutopasteNoCopy(ctx)
    fun isSmartPaste(ctx: Context): Boolean = PrefsCore.isSmartPaste(ctx)
    fun isFilterApps(ctx: Context): Boolean = PrefsCore.isFilterApps(ctx)
    fun isSilencePush(ctx: Context): Boolean = PrefsCore.isSilencePush(ctx)
    fun isHideHeadsUp(ctx: Context): Boolean = PrefsCore.isHideHeadsUp(ctx)
    fun isSounds(ctx: Context): Boolean = PrefsCore.isSounds(ctx)
    fun isOnboardingDone(ctx: Context): Boolean = PrefsCore.isOnboardingDone(ctx)

    fun setBackCopy(ctx: Context, v: Boolean) = PrefsCore.setBackCopy(ctx, v)
    fun setAutoPaste(ctx: Context, v: Boolean) = PrefsCore.setAutoPaste(ctx, v)
    fun setAutopasteNoCopy(ctx: Context, v: Boolean) = PrefsCore.setAutopasteNoCopy(ctx, v)
    fun setSmartPaste(ctx: Context, v: Boolean) = PrefsCore.setSmartPaste(ctx, v)
    fun setFilterApps(ctx: Context, v: Boolean) = PrefsCore.setFilterApps(ctx, v)
    fun setSilencePush(ctx: Context, v: Boolean) = PrefsCore.setSilencePush(ctx, v)
    fun setHideHeadsUp(ctx: Context, v: Boolean) = PrefsCore.setHideHeadsUp(ctx, v)
    fun setSounds(ctx: Context, v: Boolean) = PrefsCore.setSounds(ctx, v)
    fun setOnboardingDone(ctx: Context, v: Boolean) = PrefsCore.setOnboardingDone(ctx, v)

    fun getLanguageTyped(ctx: Context): AppLanguage = PrefsLocale.getLanguageTyped(ctx)

    /** Persist a typed [AppLanguage]. */
    fun setLanguageTyped(ctx: Context, lang: AppLanguage) = PrefsLocale.setLanguageTyped(ctx, lang)

    /** Typed accessor for the FX-level preset. */
    fun getFxLevelTyped(ctx: Context): FxLevel = PrefsFxLevel.getLevel(ctx)

    /** Persist the FX-level preset. */
    fun setFxLevel(ctx: Context, level: FxLevel) = PrefsFxLevel.setLevel(ctx, level)

    fun isFxPanelMute(c: Context): Boolean = PrefsFx.isPanelMute(c)
    fun setFxPanelMute(c: Context, v: Boolean) = PrefsFx.setPanelMute(c, v)

    fun isFxPerimEn(c: Context): Boolean = PrefsFx.isPerimEn(c)
    fun setFxPerimEn(c: Context, v: Boolean) = PrefsFx.setPerimEn(c, v)
    fun getFxPerimInten(c: Context): Int = PrefsFx.getPerimInten(c)
    fun setFxPerimInten(c: Context, v: Int) = PrefsFx.setPerimInten(c, v)

    fun isFxPerimOuterEn(c: Context): Boolean = PrefsFx.isPerimOuterEn(c)
    fun setFxPerimOuterEn(c: Context, v: Boolean) = PrefsFx.setPerimOuterEn(c, v)
    fun getFxPerimOuterW(c: Context): Int = PrefsFx.getPerimOuterW(c)
    fun setFxPerimOuterW(c: Context, v: Int) = PrefsFx.setPerimOuterW(c, v)
    fun getFxPerimOuterBl(c: Context): Int = PrefsFx.getPerimOuterBl(c)
    fun setFxPerimOuterBl(c: Context, v: Int) = PrefsFx.setPerimOuterBl(c, v)

    fun getFxPerimMidW(c: Context): Int = PrefsFx.getPerimMidW(c)
    fun setFxPerimMidW(c: Context, v: Int) = PrefsFx.setPerimMidW(c, v)
    fun getFxPerimMidBl(c: Context): Int = PrefsFx.getPerimMidBl(c)
    fun setFxPerimMidBl(c: Context, v: Int) = PrefsFx.setPerimMidBl(c, v)

    fun getFxPerimInWidthSteps(c: Context): Int = PrefsFx.getPerimInWidthSteps(c)
    fun setFxPerimInWidthSteps(c: Context, v: Int) = PrefsFx.setPerimInWidthSteps(c, v)
    fun getFxPerimInWidthDp(c: Context): Float = PrefsFx.getPerimInWidthDp(c)

    fun isFxBreathEn(c: Context): Boolean = PrefsFx.isBreathEn(c)
    fun setFxBreathEn(c: Context, v: Boolean) = PrefsFx.setBreathEn(c, v)
    fun getFxBreathAmt(c: Context): Int = PrefsFx.getBreathAmt(c)
    fun setFxBreathAmt(c: Context, v: Int) = PrefsFx.setBreathAmt(c, v)
    fun getFxBreathPeriod(c: Context): Int = PrefsFx.getBreathPeriod(c)
    fun setFxBreathPeriod(c: Context, v: Int) = PrefsFx.setBreathPeriod(c, v)

    fun isFxRotEn(c: Context): Boolean = PrefsFx.isRotEn(c)
    fun setFxRotEn(c: Context, v: Boolean) = PrefsFx.setRotEn(c, v)
    fun getFxRotPeriod(c: Context): Int = PrefsFx.getRotPeriod(c)
    fun setFxRotPeriod(c: Context, v: Int) = PrefsFx.setRotPeriod(c, v)

    fun getFxSweepStops(c: Context): Int = PrefsFx.getSweepStops(c)
    fun setFxSweepStops(c: Context, v: Int) = PrefsFx.setSweepStops(c, v)
    fun getFxSweepHueRange(c: Context): Int = PrefsFx.getSweepHueRange(c)
    fun setFxSweepHueRange(c: Context, v: Int) = PrefsFx.setSweepHueRange(c, v)

    fun getFxCountdownStyle(c: Context): Int = PrefsFx.getCountdownStyle(c)
    fun setFxCountdownStyle(c: Context, v: Int) = PrefsFx.setCountdownStyle(c, v)

    fun isFxHaloEn(c: Context): Boolean = PrefsFx.isHaloEn(c)
    fun setFxHaloEn(c: Context, v: Boolean) = PrefsFx.setHaloEn(c, v)
    fun getFxHaloInten(c: Context): Int = PrefsFx.getHaloInten(c)
    fun setFxHaloInten(c: Context, v: Int) = PrefsFx.setHaloInten(c, v)

    fun isFxWaveEn(c: Context): Boolean = PrefsFx.isWaveEn(c)
    fun setFxWaveEn(c: Context, v: Boolean) = PrefsFx.setWaveEn(c, v)
    fun getFxWaveInten(c: Context): Int = PrefsFx.getWaveInten(c)
    fun setFxWaveInten(c: Context, v: Int) = PrefsFx.setWaveInten(c, v)

    fun isFxHalftoneEn(c: Context): Boolean = PrefsFx.isHalftoneEn(c)
    fun setFxHalftoneEn(c: Context, v: Boolean) = PrefsFx.setHalftoneEn(c, v)
    fun getFxHalftoneInten(c: Context): Int = PrefsFx.getHalftoneInten(c)
    fun setFxHalftoneInten(c: Context, v: Int) = PrefsFx.setHalftoneInten(c, v)

    fun isFxSparkEn(c: Context): Boolean = PrefsFx.isSparkEn(c)
    fun setFxSparkEn(c: Context, v: Boolean) = PrefsFx.setSparkEn(c, v)
    fun getFxSparkInten(c: Context): Int = PrefsFx.getSparkInten(c)
    fun setFxSparkInten(c: Context, v: Int) = PrefsFx.setSparkInten(c, v)

    fun isFxCountdownEn(c: Context): Boolean = PrefsFx.isCountdownEn(c)
    fun setFxCountdownEn(c: Context, v: Boolean) = PrefsFx.setCountdownEn(c, v)
    fun getFxCountdownInten(c: Context): Int = PrefsFx.getCountdownInten(c)
    fun setFxCountdownInten(c: Context, v: Int) = PrefsFx.setCountdownInten(c, v)

    fun isFxBlurEn(c: Context): Boolean = PrefsFx.isBlurEn(c)
    fun setFxBlurEn(c: Context, v: Boolean) = PrefsFx.setBlurEn(c, v)

    fun getFxRevealMs(c: Context): Int = PrefsFx.getRevealMs(c)
    fun setFxRevealMs(c: Context, v: Int) = PrefsFx.setRevealMs(c, v)
    fun getFxDismissMs(c: Context): Int = PrefsFx.getDismissMs(c)
    fun setFxDismissMs(c: Context, v: Int) = PrefsFx.setDismissMs(c, v)

    fun getFxEdgeFade(c: Context): Int = PrefsFx.getEdgeFade(c)
    fun setFxEdgeFade(c: Context, v: Int) = PrefsFx.setEdgeFade(c, v)

    fun getFxCopyLottieSpeed(c: Context): Int = PrefsFx.getCopyLottieSpeed(c)
    fun setFxCopyLottieSpeed(c: Context, v: Int) = PrefsFx.setCopyLottieSpeed(c, v)
    fun getFxCopyLottieSpeedFloat(c: Context): Float = PrefsFx.getCopyLottieSpeedFloat(c)

    fun isFxCopyLottieEn(c: Context): Boolean = PrefsFx.isCopyLottieEn(c)
    fun setFxCopyLottieEn(c: Context, v: Boolean) = PrefsFx.setCopyLottieEn(c, v)

    fun isFxAutopasteLottieEn(c: Context): Boolean = PrefsFx.isAutopasteLottieEn(c)
    fun setFxAutopasteLottieEn(c: Context, v: Boolean) = PrefsFx.setAutopasteLottieEn(c, v)

    fun getAllowedApps(ctx: Context): Set<String> = PrefsFilter.getAllowedApps(ctx)
    fun setAllowedApps(ctx: Context, apps: Set<String>) = PrefsFilter.setAllowedApps(ctx, apps)

    fun getRegex(ctx: Context): String = PrefsFilter.getRegex(ctx)
    fun setRegex(ctx: Context, r: String) = PrefsFilter.setRegex(ctx, r)

    fun getTriggerWords(ctx: Context): List<String> = PrefsFilter.getTriggerWords(ctx)
    fun setTriggerWords(ctx: Context, w: List<String>) = PrefsFilter.setTriggerWords(ctx, w)
    fun resetTriggerWords(ctx: Context) = PrefsFilter.resetTriggerWords(ctx)

    fun isStopWordsEnabled(ctx: Context): Boolean = PrefsCore.isStopWordsEnabled(ctx)
    fun setStopWordsEnabled(ctx: Context, v: Boolean) = PrefsCore.setStopWordsEnabled(ctx, v)
    fun getStopWords(ctx: Context): List<String> = PrefsFilter.getStopWords(ctx)
    fun setStopWords(ctx: Context, w: List<String>) = PrefsFilter.setStopWords(ctx, w)
    fun clearStopWords(ctx: Context) = PrefsFilter.clearStopWords(ctx)

    fun isIgnoreEnabled(ctx: Context): Boolean = PrefsCore.isIgnoreEnabled(ctx)
    fun setIgnoreEnabled(ctx: Context, v: Boolean) = PrefsCore.setIgnoreEnabled(ctx, v)
    fun getIgnoredPhrases(ctx: Context): List<String> = PrefsFilter.getIgnoredPhrases(ctx)
    fun setIgnoredPhrases(ctx: Context, w: List<String>) = PrefsFilter.setIgnoredPhrases(ctx, w)
    fun resetIgnoredPhrases(ctx: Context) = PrefsFilter.resetIgnoredPhrases(ctx)

    fun isCleanupEnabled(ctx: Context): Boolean = PrefsCore.isCleanupEnabled(ctx)
    fun setCleanupEnabled(ctx: Context, v: Boolean) = PrefsCore.setCleanupEnabled(ctx, v)
    fun getCleanupPhrases(ctx: Context): List<String> = PrefsFilter.getCleanupPhrases(ctx)
    fun setCleanupPhrases(ctx: Context, w: List<String>) = PrefsFilter.setCleanupPhrases(ctx, w)
    fun resetCleanupPhrases(ctx: Context) = PrefsFilter.resetCleanupPhrases(ctx)

    fun isCurrencySkipEnabled(ctx: Context): Boolean = PrefsCore.isCurrencySkipEnabled(ctx)
    fun setCurrencySkipEnabled(ctx: Context, v: Boolean) = PrefsCore.setCurrencySkipEnabled(ctx, v)
    fun getCurrencyTokens(ctx: Context): List<String> = PrefsFilter.getCurrencyTokens(ctx)
    fun setCurrencyTokens(ctx: Context, w: List<String>) = PrefsFilter.setCurrencyTokens(ctx, w)
    fun resetCurrencyTokens(ctx: Context) = PrefsFilter.resetCurrencyTokens(ctx)

    fun isSkipForeground(ctx: Context): Boolean = PrefsCore.isSkipForeground(ctx)
    fun setSkipForeground(ctx: Context, v: Boolean) = PrefsCore.setSkipForeground(ctx, v)
    fun isNormalizeDigits(ctx: Context): Boolean = PrefsCore.isNormalizeDigits(ctx)
    fun setNormalizeDigits(ctx: Context, v: Boolean) = PrefsCore.setNormalizeDigits(ctx, v)

    /** Typed accessor for the display-mode preset. */
    fun getDisplayModeTyped(ctx: Context): DisplayMode = PrefsCore.getDisplayModeTyped(ctx)

    /** Persist the display-mode preset. */
    fun setDisplayMode(ctx: Context, mode: DisplayMode) = PrefsCore.setDisplayModeTyped(ctx, mode)

    /** Convenience: `true` iff the active display mode is [DisplayMode.SHADE]. */
    fun isShadeMode(ctx: Context): Boolean = getDisplayModeTyped(ctx) == DisplayMode.SHADE

    fun isBackToShade(ctx: Context): Boolean = PrefsCore.isBackToShade(ctx)
    fun setBackToShade(ctx: Context, v: Boolean) = PrefsCore.setBackToShade(ctx, v)

    fun isCloseToShade(ctx: Context): Boolean = PrefsCore.isCloseToShade(ctx)
    fun setCloseToShade(ctx: Context, v: Boolean) = PrefsCore.setCloseToShade(ctx, v)

    fun getShadeDurationMs(ctx: Context): Int = PrefsCore.getShadeDurationMs(ctx)
    fun setShadeDurationMs(ctx: Context, ms: Int) = PrefsCore.setShadeDurationMs(ctx, ms)

    fun isPackageAllowed(ctx: Context, pkg: String?): Boolean = PrefsFilter.isPackageAllowed(ctx, pkg)

    /**
     * Reset every preference to its default while preserving the
     * onboarding-done flag, then drop the in-memory phrase-list
     * caches so the next reader sees the freshly cleared state.
     */
    fun resetAllExceptOnboarding(ctx: Context) {
        PrefsCore.clearAllExceptOnboarding(ctx)
        PrefsFilter.invalidatePhraseCaches()
    }
}
