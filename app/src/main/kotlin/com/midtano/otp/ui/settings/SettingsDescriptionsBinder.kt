// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.app.Activity
import android.content.Context
import android.widget.TextView
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import com.midtano.otp.locale.LocaleSwitcher
import com.midtano.otp.util.TextSwapAnimator

/**
 * Manages the "smart" description TextViews under the Settings
 * toggles. Their text depends on the current toggle state, not on
 * a static `@string/` value, so we keep them out of the
 * [LocaleSwitcher] retag system and refresh them manually on every
 * toggle change and locale change.
 *
 * Each text change goes through [TextSwapAnimator] so the
 * description swap is visible (slide-up animation matched to the
 * SpringSwitch toggle timing). When the no-op check inside the
 * animator detects the new text is identical to the current one,
 * nothing animates — so toggling one switch only animates the one
 * description that actually changed.
 *
 * Public surface:
 * - [update] — refresh every dynamic description from current
 *   [Prefs] (called on `onResume`, `onConfigurationChanged` and
 *   after each "always-shown" toggle in [SettingsActivity]).
 * - [applyAnimated] — used by every per-feature binder
 *   ([SettingsExtractionBinder], [SettingsDisplayModeBinder],
 *   [SettingsStopWordsBinder], [SettingsPhraseListBinder]) to
 *   route their own toggle-driven description changes through the
 *   same swipe-up animation, so every switch in the screen feels
 *   identical.
 * - [DYNAMIC_DESC_IDS] / [clearDynamicDescTags] — keeps the locale
 *   switcher from re-applying inflate-time `@string/` values that
 *   no longer match the current toggle state.
 */
internal object SettingsDescriptionsBinder {

    /**
     * IDs of every dynamic-description TextView managed manually.
     * The locale switcher consults this list and skips re-applying
     * inflate-time `@string/` values to them, so a language change
     * does not overwrite the freshly-chosen on/off description.
     */
    val DYNAMIC_DESC_IDS: IntArray = intArrayOf(
        // "Always-on" toggles handled by [update].
        R.id.tv_filter_mode,
        R.id.tv_auto_paste_desc,
        R.id.tv_autopaste_no_copy_desc,
        R.id.tv_smart_paste_desc,
        R.id.tv_hide_headsup_desc,
        R.id.tv_silence_push_desc,
        R.id.tv_back_action_desc,
        R.id.tv_sounds_desc,
        R.id.tv_copy_lottie_desc,
        R.id.tv_autopaste_lottie_desc,

        // Display-mode and shade-related descriptions, owned by
        // [SettingsDisplayModeBinder] but routed through this binder
        // for the same animated swap.
        R.id.tv_back_to_shade_desc,
        R.id.tv_close_to_shade_desc,

        // Extraction-pipeline toggles, owned by
        // [SettingsExtractionBinder].
        R.id.tv_skip_fg_desc,
        R.id.tv_normalize_desc,
        R.id.tv_ignore_desc,
        R.id.tv_cleanup_desc,
        R.id.tv_currency_desc,

        // Trigger / stop-word descriptions, owned by
        // [SettingsTriggerWordsBinder] / [SettingsStopWordsBinder].
        R.id.tv_stop_words_desc,

        // FX-tuner master toggle, owned by [SettingsFxTunerBinder].
        R.id.tv_fx_panel_mute_desc,
    )

    /**
     * Refresh every dynamic description from current [Prefs].
     *
     * Every TextView listed in [DYNAMIC_DESC_IDS] is touched here
     * so a locale change picks up the new translation in one pass —
     * including the extraction-pipeline toggles, the shade /
     * back-to-shade switches, the FX panel-mute master and the
     * stop-words switch. Leaving any of them out makes the on-screen
     * description stick to the old language until the user toggles
     * the matching switch or restarts the activity.
     *
     * @param animate when `false`, set the new text instantly with
     *                no animation. Used right after a locale change
     *                so the descriptions silently re-translate to
     *                the new language; the next toggle then only
     *                animates the single description that actually
     *                changed.
     */
    fun update(host: Activity, animate: Boolean) {
        // ── always-on toggles bound directly in SettingsActivity ──
        applyAnimated(
            host,
            R.id.tv_sounds_desc,
            Prefs.isSounds(host),
            R.string.desc_sounds_on,
            R.string.desc_sounds_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_copy_lottie_desc,
            Prefs.isFxCopyLottieEn(host),
            R.string.desc_copy_lottie_on,
            R.string.desc_copy_lottie_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_autopaste_lottie_desc,
            Prefs.isFxAutopasteLottieEn(host),
            R.string.desc_autopaste_lottie_on,
            R.string.desc_autopaste_lottie_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_auto_paste_desc,
            Prefs.isAutoPaste(host),
            R.string.desc_autopaste_on,
            R.string.desc_autopaste_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_autopaste_no_copy_desc,
            Prefs.isAutopasteNoCopy(host),
            R.string.desc_no_copy_on,
            R.string.desc_no_copy_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_smart_paste_desc,
            Prefs.isSmartPaste(host),
            R.string.desc_smart_paste_on,
            R.string.desc_smart_paste_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_hide_headsup_desc,
            Prefs.isHideHeadsUp(host),
            R.string.desc_hide_headsup_on,
            R.string.desc_hide_headsup_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_silence_push_desc,
            Prefs.isSilencePush(host),
            R.string.desc_silence_on,
            R.string.desc_silence_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_back_action_desc,
            Prefs.isBackCopy(host),
            R.string.desc_back_copy_on,
            R.string.desc_back_copy_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_filter_mode,
            Prefs.isFilterApps(host),
            R.string.desc_filter_on,
            R.string.desc_filter_off,
            animate,
        )

        // ── display-mode + shade routing (SettingsDisplayModeBinder) ──
        applyAnimated(
            host,
            R.id.tv_back_to_shade_desc,
            Prefs.isBackToShade(host),
            R.string.desc_back_to_shade_on,
            R.string.desc_back_to_shade_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_close_to_shade_desc,
            Prefs.isCloseToShade(host),
            R.string.desc_close_to_shade_on,
            R.string.desc_close_to_shade_off,
            animate,
        )

        // ── extraction-pipeline toggles (SettingsExtractionBinder) ──
        applyAnimated(
            host,
            R.id.tv_skip_fg_desc,
            Prefs.isSkipForeground(host),
            R.string.desc_skip_fg_on,
            R.string.desc_skip_fg_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_normalize_desc,
            Prefs.isNormalizeDigits(host),
            R.string.desc_normalize_on,
            R.string.desc_normalize_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_ignore_desc,
            Prefs.isIgnoreEnabled(host),
            R.string.desc_ignore_on,
            R.string.desc_ignore_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_cleanup_desc,
            Prefs.isCleanupEnabled(host),
            R.string.desc_cleanup_on,
            R.string.desc_cleanup_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_currency_desc,
            Prefs.isCurrencySkipEnabled(host),
            R.string.desc_currency_on,
            R.string.desc_currency_off,
            animate,
        )

        // ── stop-words / FX panel-mute (Settings*Binder) ──
        applyAnimated(
            host,
            R.id.tv_stop_words_desc,
            Prefs.isStopWordsEnabled(host),
            R.string.desc_stop_on,
            R.string.desc_stop_off,
            animate,
        )
        applyAnimated(
            host,
            R.id.tv_fx_panel_mute_desc,
            Prefs.isFxPanelMute(host),
            R.string.desc_panel_mute_on,
            R.string.desc_panel_mute_off,
            animate,
        )
    }

    /**
     * Set the on/off description for [descId] using the current
     * [enabled] flag. The text swap goes through [TextSwapAnimator]
     * so every binder picks up the same swipe-up animation that the
     * "always-on" toggles already use.
     *
     * @param animate when `true` (default), animate the swap;
     *                pass `false` to set the text instantly (used
     *                after a locale change so the on-screen text
     *                silently re-translates without a swipe).
     */
    fun applyAnimated(
        host: Context,
        descId: Int,
        enabled: Boolean,
        onResId: Int,
        offResId: Int,
        animate: Boolean = true,
    ) {
        val view = (host as? Activity)?.findViewById<TextView?>(descId) ?: return
        val text = host.getString(if (enabled) onResId else offResId)
        if (animate) TextSwapAnimator.animateTo(view, text) else view.text = text
    }

    /**
     * Clear locale tags on dynamic-description TextViews so
     * [LocaleSwitcher] skips them. The correct on/off text is then
     * re-applied manually after a locale change.
     */
    fun clearDynamicDescTags(host: Activity) {
        for (id in DYNAMIC_DESC_IDS) {
            host.findViewById<android.view.View?>(id)?.let { v ->
                v.setTag(R.id.locale_text_res, null)
                v.setTag(R.id.locale_hint_res, null)
            }
        }
    }
}
