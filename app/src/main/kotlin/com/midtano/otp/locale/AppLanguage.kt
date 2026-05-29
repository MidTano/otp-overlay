// SPDX-License-Identifier: MIT
package com.midtano.otp.locale

import java.util.Locale

/**
 * Single source of truth for the languages this app ships with.
 *
 * The on-disk format ([tag]) is the BCP-47 primary subtag — `en` /
 * `ru` — so an existing [android.content.SharedPreferences] file
 * written by an older version of [com.midtano.otp.data.prefs.PrefsLocale]
 * decodes without migration.
 *
 * Every locale-fallback decision goes through [fromTag] so an
 * unknown stored value (older build, manual edit, future locale
 * picker) always lands on a deterministic [DEFAULT] — and never
 * falls back to [RU] just because the stored value isn't `en`,
 * which was the bug in the previous bespoke routing.
 *
 * Public because it leaks through the public [com.midtano.otp.data.Prefs]
 * facade as a parameter / return type; consumers outside this module
 * should still prefer the typed `Prefs.*Typed` accessors.
 */
enum class AppLanguage(val tag: String, val locale: Locale) {

    /** English — the source language of every translatable resource. */
    EN("en", Locale.ENGLISH),

    /** Russian — primary localisation. */
    RU("ru", Locale.forLanguageTag("ru")),
    ;

    companion object {

        /**
         * Default applied when no stored value or system locale
         * matches a known entry. English is the source language of
         * every `res/values/strings.xml` entry, so an unknown locale
         * resolves to it rather than to the unrelated [RU].
         */
        val DEFAULT: AppLanguage = EN

        /**
         * Decode a BCP-47 primary subtag (`en`, `ru`, `en-US`, …)
         * to one of the known entries.
         *
         * Comparison is case-insensitive on the leading subtag so
         * `EN` / `en-US` / `eng` all map to [EN].
         *
         * @return the matching entry, or [DEFAULT] when [tag] is
         *         null, empty, or unrecognised.
         */
        fun fromTag(tag: String?): AppLanguage = parseExact(tag) ?: DEFAULT

        /**
         * Strict counterpart to [fromTag] that returns `null` when
         * [tag] does not name a language we ship for. Use this on
         * fallback chains where "unknown" must be distinguishable
         * from "explicitly the default" so the next fallback link
         * still gets a chance to fire.
         */
        fun parseExact(tag: String?): AppLanguage? {
            if (tag.isNullOrEmpty()) return null
            // BCP-47 primary subtag is everything up to the first
            // separator (`-` or `_`); the rest carries region and
            // variant which we intentionally ignore.
            val primary = tag.substringBefore('-').substringBefore('_')
            for (entry in entries) {
                if (entry.tag.equals(primary, ignoreCase = true)) return entry
            }
            return null
        }
    }
}
