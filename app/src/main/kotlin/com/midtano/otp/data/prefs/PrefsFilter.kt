// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

import android.content.Context
import androidx.core.content.edit
import com.midtano.otp.extractor.OtpExtractor

/**
 * Per-app allow list, custom regex and the five phrase lists that
 * gate whether a given message is treated as carrying an OTP.
 *
 * Read APIs return immutable `List<String>`. To mutate a list build a
 * new one (`current + value`, `current.filterNot { ... }`) and pass it
 * to the matching setter.
 *
 * On disk every list is a newline-separated string. Empty / blank
 * entries are dropped, duplicates are collapsed (case-sensitive for
 * regex patterns and currency symbols, case-insensitive for trigger
 * and stop words).
 */
object PrefsFilter {

    internal const val KEY_ALLOWED_APPS = "allowed_apps"
    internal const val KEY_REGEX = "otp_regex"

    /**
     * Stored as newline-separated text rather than a [Set] so the
     * user's authoring order survives reads — a [Set] would shuffle
     * the editor every time Settings opened.
     */
    internal const val KEY_TRIGGER_WORDS = "trigger_words"
    internal const val KEY_STOP_WORDS = "stop_words"

    /** Phrases that block detection entirely (e.g. `barcode`, `vscode`). */
    internal const val KEY_IGNORED_PHRASES = "ignored_phrases"

    /** Phrases stripped from the message body before code extraction. */
    internal const val KEY_CLEANUP_PHRASES = "cleanup_phrases"

    /** Tokens that mark adjacent digits as a currency amount. */
    internal const val KEY_CURRENCY_TOKENS = "currency_tokens"

    /**
     * Default trigger keywords. Multilingual by design — only one of
     * these has to match to gate the extractor.
     *
     * Pure-ASCII entries get strict word-boundary matching on both
     * sides (so `code` does not match inside `CodeRabbit`); non-ASCII
     * entries get left-side-only boundary matching, so stems like
     * `одноразов` match `одноразовый`, `одноразовая`, etc.
     */
    val DEFAULT_TRIGGER_WORDS: Array<String> = arrayOf(
        // Russian / Cyrillic
        "код", "пин", "введите", "одноразов", "верифик", "подтвержд",
        // English
        "code", "otp", "pin", "passcode", "passcodes",
        "one-time", "one time", "onetime",
        "verif", "verification", "auth code", "authcode",
        "2fa", "mfa", "token", "security code",
        // Transliterated
        "kod", "parol",
        // German
        "einmalkennwort", "bestätigungscode", "verifizierungscode",
        // Spanish
        "código", "codigo", "clave", "contraseña",
        // French
        "code de vérification", "code à usage unique",
        // Italian
        "codice",
        // Portuguese
        "código de verificação",
        // Polish
        "kod weryfikacyjny", "kod autoryzacji",
        // Turkish
        "doğrulama kodu", "kodu", "kodunuz", "şifre", "sifre",
        // Finnish
        "vahvistuskoodi", "kertakäyttökoodi",
        // Latvian
        "kods",
        // Romanian
        "cod de verificare",
        // Ukrainian
        "код підтвердження",
        // Arabic
        "كود", "رمز",
        // Persian
        "کد",
        // Hebrew
        "קוד", "סיסמ",
        // Chinese
        "验证码", "校验码", "識別碼", "認證", "驗證",
        // Japanese
        "コード", "パスワード", "認証番号", "ワンタイム",
        // Korean
        "인증번호", "확인 코드",
        // Hindi
        "ओटीपी", "कोड",
        // Vietnamese
        "mã xác minh", "mã otp",
        // Indonesian / Malay
        "kode verifikasi", "kod pengesahan",
        // mTAN family
        "tan", "mtan", "smstan",
    )

    /**
     * Default ignore phrases. A message containing any of these is
     * dropped before extraction, which suppresses false positives
     * from brand names, source-code identifiers and discount-code
     * marketing.
     */
    val DEFAULT_IGNORED_PHRASES: Array<String> = arrayOf(
        "vscode", "versionCode", "unicode", "barcode", "fancode",
        "encode", "decode", "codex",
        "discount code", "promo code", "coupon code",
        "RatingCode",
    )

    /**
     * Default cleanup phrases. Each is a Java regex stripped from the
     * body before extraction — domain names, "card ending NNNN"
     * fragments, the SMS Retriever `<#>` marker and quote characters.
     */
    val DEFAULT_CLEANUP_PHRASES: Array<String> = arrayOf(
        "[a-zA-Z0-9][a-zA-Z0-9-]{0,61}\\.[a-zA-Z]{2,}(?:[.a-zA-Z]{0,3}(?=\\s+)|)",
        "['\"]",
        "Endziffer-\\d+",
        "Ending \\d+",
        "<#>",
        "share OTP",
    )

    /**
     * Default currency tokens. Any 4..9 digit number adjacent to one
     * of these is treated as a monetary amount, not an OTP.
     */
    val DEFAULT_CURRENCY_TOKENS: Array<String> = arrayOf(
        "USD", "EUR", "GBP", "RUB", "BYN", "KZT", "UAH", "AMD", "AZN",
        "JPY", "CNY", "TRY", "INR", "BRL", "MXN", "PLN", "CZK", "CHF",
        "SEK", "NOK", "DKK", "AED", "SAR",
        "руб", "грн", "тенге",
        "$", "€", "£", "₽", "¥", "₴", "₸", "₺", "₹",
    )

    private val triggerWords = CachedPhraseList(
        KEY_TRIGGER_WORDS,
        DEFAULT_TRIGGER_WORDS,
        lowercase = true,
    )
    private val stopWords = CachedPhraseList(
        KEY_STOP_WORDS,
        defaults = null,
        lowercase = true,
    )
    private val ignoredPhrases = CachedPhraseList(
        KEY_IGNORED_PHRASES,
        DEFAULT_IGNORED_PHRASES,
        lowercase = false,
    )
    private val cleanupPhrases = CachedPhraseList(
        KEY_CLEANUP_PHRASES,
        DEFAULT_CLEANUP_PHRASES,
        lowercase = false,
    )
    private val currencyTokens = CachedPhraseList(
        KEY_CURRENCY_TOKENS,
        DEFAULT_CURRENCY_TOKENS,
        lowercase = false,
    )

    fun getAllowedApps(c: Context): Set<String> =
        PrefsFile.sp(c).getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()

    fun setAllowedApps(c: Context, apps: Set<String>) {
        PrefsFile.sp(c).edit { putStringSet(KEY_ALLOWED_APPS, apps) }
    }

    fun getRegex(c: Context): String =
        PrefsFile.sp(c).getString(KEY_REGEX, OtpExtractor.DEFAULT_REGEX) ?: OtpExtractor.DEFAULT_REGEX

    fun setRegex(c: Context, regex: String) {
        PrefsFile.sp(c).edit { putString(KEY_REGEX, regex) }
    }

    fun getTriggerWords(c: Context): List<String> = triggerWords.get(c)
    fun setTriggerWords(c: Context, words: List<String>) = triggerWords.set(c, words)
    fun resetTriggerWords(c: Context) = triggerWords.reset(c)

    fun getStopWords(c: Context): List<String> = stopWords.get(c)
    fun setStopWords(c: Context, words: List<String>) = stopWords.set(c, words)
    fun clearStopWords(c: Context) = stopWords.reset(c)

    fun getIgnoredPhrases(c: Context): List<String> = ignoredPhrases.get(c)
    fun setIgnoredPhrases(c: Context, words: List<String>) = ignoredPhrases.set(c, words)
    fun resetIgnoredPhrases(c: Context) = ignoredPhrases.reset(c)

    fun getCleanupPhrases(c: Context): List<String> = cleanupPhrases.get(c)
    fun setCleanupPhrases(c: Context, words: List<String>) = cleanupPhrases.set(c, words)
    fun resetCleanupPhrases(c: Context) = cleanupPhrases.reset(c)

    fun getCurrencyTokens(c: Context): List<String> = currencyTokens.get(c)
    fun setCurrencyTokens(c: Context, words: List<String>) = currencyTokens.set(c, words)
    fun resetCurrencyTokens(c: Context) = currencyTokens.reset(c)

    /** True if [pkg] is allowed under the current filter mode. */
    fun isPackageAllowed(c: Context, pkg: String?): Boolean {
        if (!PrefsCore.isFilterApps(c)) return true
        return pkg != null && pkg in getAllowedApps(c)
    }

    /**
     * Drop every in-memory phrase-list cache so the next reader sees
     * the freshly persisted value. Invoked by the public facade after
     * any "clear all" wipe to keep the caches consistent.
     */
    fun invalidatePhraseCaches() {
        triggerWords.invalidate()
        stopWords.invalidate()
        ignoredPhrases.invalidate()
        cleanupPhrases.invalidate()
        currencyTokens.invalidate()
    }

    /**
     * Per-list cache backed by a snapshot of the raw stored string.
     * A read with the same `raw` returns the cached immutable list;
     * any [set] / [reset] invalidates the cache. The cache is keyed
     * on the raw value so an out-of-band wipe (e.g. `clear()` from
     * the public facade) is detected on the next read.
     */
    private class CachedPhraseList(
        private val key: String,
        private val defaults: Array<String>?,
        private val lowercase: Boolean,
    ) {
        @Volatile private var cachedRaw: String? = null

        @Volatile private var cachedList: List<String>? = null

        fun get(c: Context): List<String> {
            val raw = PrefsFile.sp(c).getString(key, null)
            cachedList?.let { cached ->
                if (raw == cachedRaw) return cached
            }
            val parsed: List<String> = if (raw == null) {
                defaults?.toList() ?: emptyList()
            } else {
                PhraseListStore.parse(raw, lowercase)
            }
            cachedRaw = raw
            cachedList = parsed
            return parsed
        }

        fun set(c: Context, words: List<String>) {
            PrefsFile.sp(c).edit { putString(key, PhraseListStore.join(words, lowercase)) }
            invalidate()
        }

        fun reset(c: Context) {
            PrefsFile.sp(c).edit { remove(key) }
            invalidate()
        }

        fun invalidate() {
            cachedRaw = null
            cachedList = null
        }
    }
}
