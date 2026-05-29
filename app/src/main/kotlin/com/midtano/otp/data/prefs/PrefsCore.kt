// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

import android.content.Context
import androidx.core.content.edit

/**
 * General app-behaviour toggles: back-tap action, auto-paste, sounds,
 * onboarding, the extraction-pipeline switches and the shade-mode
 * preferences.
 *
 * Every method here is forwarded to from [com.midtano.otp.data.Prefs]
 * so call sites consume the public facade rather than this object
 * directly.
 */
object PrefsCore {

    internal const val KEY_BACK_COPY = "back_copy"
    internal const val KEY_AUTO_PASTE = "auto_paste"
    internal const val KEY_AUTOPASTE_NO_COPY = "autopaste_no_copy"
    internal const val KEY_SMART_PASTE = "smart_paste"
    internal const val KEY_FILTER_APPS = "filter_apps_enabled"
    internal const val KEY_SILENCE_PUSH = "silence_push"
    internal const val KEY_HIDE_HEADSUP = "hide_headsup"
    internal const val KEY_SOUNDS = "sounds"
    internal const val KEY_ONBOARDING = "onboarding_done"
    internal const val KEY_STOP_WORDS_EN = "stop_words_enabled"
    internal const val KEY_IGNORE_EN = "ignore_phrases_enabled"
    internal const val KEY_CLEANUP_EN = "cleanup_phrases_enabled"
    internal const val KEY_CURRENCY_EN = "currency_skip_enabled"
    internal const val KEY_SKIP_FOREGROUND = "skip_foreground_notifs"
    internal const val KEY_NORMALIZE_DIGITS = "normalize_digits"
    internal const val KEY_DISPLAY_MODE = "display_mode"
    internal const val KEY_BACK_TO_SHADE = "back_to_shade"
    internal const val KEY_CLOSE_TO_SHADE = "close_to_shade"
    internal const val KEY_SHADE_DURATION_MS = "shade_duration_ms"

    const val SHADE_DURATION_MIN_MS: Int = 30_000
    const val SHADE_DURATION_MAX_MS: Int = 300_000
    const val SHADE_DURATION_DEFAULT_MS: Int = 60_000

    /** Resolve the persisted display mode as a typed [DisplayMode]. */
    fun getDisplayModeTyped(c: Context): DisplayMode =
        DisplayMode.fromStorageId(PrefsFile.sp(c).getInt(KEY_DISPLAY_MODE, DisplayMode.DEFAULT.storageId))

    /** Persist a typed [DisplayMode]. */
    fun setDisplayModeTyped(c: Context, mode: DisplayMode) {
        PrefsFile.sp(c).edit { putInt(KEY_DISPLAY_MODE, mode.storageId) }
    }

    fun isBackCopy(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_BACK_COPY, false)
    fun isAutoPaste(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_AUTO_PASTE, false)
    fun isAutopasteNoCopy(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_AUTOPASTE_NO_COPY, false)
    fun isSmartPaste(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_SMART_PASTE, true)
    fun isFilterApps(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_FILTER_APPS, false)
    fun isSilencePush(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_SILENCE_PUSH, false)
    fun isHideHeadsUp(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_HIDE_HEADSUP, true)
    fun isSounds(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_SOUNDS, true)
    fun isOnboardingDone(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_ONBOARDING, false)
    fun isStopWordsEnabled(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_STOP_WORDS_EN, true)
    fun isIgnoreEnabled(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_IGNORE_EN, true)
    fun isCleanupEnabled(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_CLEANUP_EN, true)
    fun isCurrencySkipEnabled(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_CURRENCY_EN, true)
    fun isSkipForeground(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_SKIP_FOREGROUND, true)
    fun isNormalizeDigits(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_NORMALIZE_DIGITS, true)

    fun isBackToShade(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_BACK_TO_SHADE, false)

    fun isCloseToShade(c: Context): Boolean = PrefsFile.sp(c).getBoolean(KEY_CLOSE_TO_SHADE, false)

    fun getShadeDurationMs(c: Context): Int =
        PrefsFile.clampI(
            PrefsFile.sp(c).getInt(KEY_SHADE_DURATION_MS, SHADE_DURATION_DEFAULT_MS),
            SHADE_DURATION_MIN_MS,
            SHADE_DURATION_MAX_MS,
        )

    fun setBackCopy(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_BACK_COPY, v) }
    fun setAutoPaste(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_AUTO_PASTE, v) }
    fun setAutopasteNoCopy(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_AUTOPASTE_NO_COPY, v) }
    fun setSmartPaste(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_SMART_PASTE, v) }
    fun setFilterApps(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_FILTER_APPS, v) }
    fun setSilencePush(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_SILENCE_PUSH, v) }
    fun setHideHeadsUp(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_HIDE_HEADSUP, v) }
    fun setSounds(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_SOUNDS, v) }
    fun setOnboardingDone(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_ONBOARDING, v) }
    fun setStopWordsEnabled(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_STOP_WORDS_EN, v) }
    fun setIgnoreEnabled(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_IGNORE_EN, v) }
    fun setCleanupEnabled(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_CLEANUP_EN, v) }
    fun setCurrencySkipEnabled(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_CURRENCY_EN, v) }
    fun setSkipForeground(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_SKIP_FOREGROUND, v) }
    fun setNormalizeDigits(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_NORMALIZE_DIGITS, v) }

    fun setBackToShade(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_BACK_TO_SHADE, v) }
    fun setCloseToShade(c: Context, v: Boolean) = PrefsFile.sp(c).edit { putBoolean(KEY_CLOSE_TO_SHADE, v) }

    fun setShadeDurationMs(c: Context, ms: Int) {
        val v = PrefsFile.clampI(ms, SHADE_DURATION_MIN_MS, SHADE_DURATION_MAX_MS)
        PrefsFile.sp(c).edit { putInt(KEY_SHADE_DURATION_MS, v) }
    }

    /**
     * Wipe the entire SharedPreferences file, then re-arm the
     * onboarding-done flag so a "Reset all" tap does not bounce the
     * user back into the onboarding flow.
     *
     * Phrase-list caches are flushed by the public facade after this
     * call to avoid a layering cycle.
     */
    fun clearAllExceptOnboarding(c: Context) {
        val wasOnboarded = isOnboardingDone(c)
        PrefsFile.sp(c).edit { clear() }
        if (wasOnboarded) setOnboardingDone(c, true)
    }
}
