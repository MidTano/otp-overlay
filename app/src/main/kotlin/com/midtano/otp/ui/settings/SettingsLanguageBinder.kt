// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.midtano.otp.R
import com.midtano.otp.core.BaseActivity
import com.midtano.otp.data.Prefs
import com.midtano.otp.locale.AppLanguage
import com.midtano.otp.locale.LocaleSwitcher

/**
 * Wires the language-picker tile in Settings.
 *
 * The picker toggles between [AppLanguage.RU] and [AppLanguage.EN]
 * on each tap, routing through
 * [AppCompatDelegate.setApplicationLocales] (the platform
 * `LocaleManager` on Android 13+). Every activity declares
 * `configChanges="locale"` in the manifest, so the change comes in
 * via `onConfigurationChanged` (handled in [BaseActivity]) and
 * triggers a smooth swipe across all locale-tagged TextViews — no
 * flicker, no recreate.
 */
internal object SettingsLanguageBinder {

    fun bind(host: Activity) {
        val btn = host.findViewById<TextView?>(R.id.btn_language) ?: return
        val desc = host.findViewById<TextView?>(R.id.tv_language_desc)

        // Both views are populated dynamically based on the current
        // language preference, so they must NOT be retagged with the
        // inflate-time `@string/` value. Without this, every locale
        // change triggers BaseActivity → LocaleSwitcher.refresh,
        // which walks every tagged TextView and re-applies the
        // inflate-time string — for the language button that meant
        // a "EN → … → RU" bounce immediately after the user picked
        // EN, because the layout's default text is `settings_lang_default = "RU"`.
        btn.setTag(R.id.locale_text_res, null)
        btn.setTag(R.id.locale_hint_res, null)
        desc?.setTag(R.id.locale_text_res, null)
        desc?.setTag(R.id.locale_hint_res, null)

        fun updateUiInstant() {
            val lang = Prefs.getLanguageTyped(host)
            btn.text = host.getString(buttonLabelRes(lang))
            desc?.text = host.getString(descriptionRes(lang))
        }
        updateUiInstant()

        btn.setOnClickListener {
            val current = Prefs.getLanguageTyped(host)
            val next = nextLanguage(current)
            // ru → en swipes left, en → ru swipes right.
            val swipeLeft = next == AppLanguage.EN
            (host as? BaseActivity)?.setPendingSwipeLeft(swipeLeft)

            Prefs.setLanguageTyped(host, next)

            // Animate the language button label too.
            LocaleSwitcher.swipeText(
                btn,
                host.getString(buttonLabelRes(next)),
                swipeLeft,
            )
            if (desc != null) {
                LocaleSwitcher.swipeText(
                    desc,
                    getResForLocale(host, next, descriptionRes(next)),
                    swipeLeft,
                )
            }

            // Trigger the system locale change. With
            // `configChanges="locale"` this comes in as
            // onConfigurationChanged — no recreate, no flicker.
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next.tag))
        }
    }

    /** Resolve a string in a specific [AppLanguage] without changing the activity's. */
    fun getResForLocale(host: Activity, lang: AppLanguage, resId: Int): String = try {
        val cfg = Configuration(host.resources.configuration).apply { setLocale(lang.locale) }
        host.createConfigurationContext(cfg).resources.getString(resId)
    } catch (_: Resources.NotFoundException) {
        // Translation missing for this resource in the target locale.
        host.getString(resId)
    }

    /** Step to the next language in the round-robin picker. */
    private fun nextLanguage(current: AppLanguage): AppLanguage = when (current) {
        AppLanguage.EN -> AppLanguage.RU
        AppLanguage.RU -> AppLanguage.EN
    }

    /** Two-letter button label resource for [lang]. */
    private fun buttonLabelRes(lang: AppLanguage): Int = when (lang) {
        AppLanguage.EN -> R.string.lang_code_en
        AppLanguage.RU -> R.string.lang_code_ru
    }

    /** Long description resource for [lang]. */
    private fun descriptionRes(lang: AppLanguage): Int = when (lang) {
        AppLanguage.EN -> R.string.lang_english
        AppLanguage.RU -> R.string.lang_russian
    }
}
