// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.midtano.otp.locale.AppLanguage
import java.util.Locale

/**
 * App language preference.
 *
 * Reads in this order:
 * 1. [AppCompatDelegate.getApplicationLocales] — authoritative on
 *    Android 13+ via the platform `LocaleManager`.
 * 2. [PrefsFile] — the value the user picked before the platform API
 *    was available.
 * 3. [Locale.getDefault] — best-effort fallback that picks Russian on
 *    a Russian device and English everywhere else.
 *
 * Every fallback is routed through [AppLanguage.fromTag] so an
 * unknown stored value or a system locale this app does not ship
 * for (e.g. `de`, `fr`, `zh`) lands on [AppLanguage.DEFAULT] — i.e.
 * English — instead of silently flipping the UI to Russian, which
 * was the bug in the previous bespoke decoder.
 */
object PrefsLocale {

    internal const val KEY_LANGUAGE = "app_language"

    /**
     * Typed accessor for the resolved language. Prefer this over
     * the untyped [PrefsFile] read in new code so the call site
     * stays compile-time safe against the closed [AppLanguage] set.
     */
    fun getLanguageTyped(c: Context): AppLanguage {
        runCatching {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (!locales.isEmpty) {
                // Strict parse: only honour the platform value when
                // it names a language this app actually ships for.
                // Anything else falls through to the SharedPreferences
                // value below — without this, an unrelated platform
                // override (e.g. `de`) would coerce the UI to the
                // [AppLanguage.DEFAULT] (English) even though the
                // user explicitly stored `ru`.
                val mapped = AppLanguage.parseExact(locales[0]?.toLanguageTag())
                if (mapped != null) return mapped
            }
        }
        val stored = PrefsFile.sp(c).getString(KEY_LANGUAGE, null)
        AppLanguage.parseExact(stored)?.let { return it }
        runCatching {
            AppLanguage.parseExact(Locale.getDefault().language)?.let { return it }
        }
        return AppLanguage.DEFAULT
    }

    fun setLanguageTyped(c: Context, lang: AppLanguage) {
        PrefsFile.sp(c).edit { putString(KEY_LANGUAGE, lang.tag) }
    }
}
