// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import android.content.Context
import com.midtano.otp.data.Prefs

/**
 * Public, Context-aware OTP extractor.
 *
 * A thin shim over [OtpExtractorCore]: it snapshots every relevant
 * preference into an [OtpExtractorSettings] record and forwards the
 * call. All the actual logic — truncation, digit normalisation,
 * ignore / cleanup / trigger / stop-word gates, ReDoS-protected
 * regex run, currency-adjacency rejection and the split-code
 * fallback — lives in the core so JVM unit tests can exercise it
 * without an Android runtime.
 *
 * Companion helpers:
 * - [OtpTriggers]      — keyword gate primitives.
 * - [OtpRegexCache]    — compiled-regex memoisation reused by
 *                        callers that build their own pipeline
 *                        (e.g. [OtpDiagnoser]).
 * - [OtpDiagnoser]     — human-readable test report.
 */
internal object OtpExtractor {

    /**
     * Default regex matches 4..9 digit codes that:
     * - start at the beginning of the message OR are preceded by a
     *   non-digit / non-dot character, so embedded substrings like
     *   `v4.0.999`, `+79161234567` and `46.216.168.119` never match;
     * - end at the end of the string, are followed by a non-digit /
     *   non-dot character, OR end with a sentence period that is not
     *   followed by another digit. So `Your code is 9001.` matches
     *   while `1234.5678` does not.
     */
    const val DEFAULT_REGEX: String =
        "(?:^|(?<=[^0-9.]))([0-9]{4,9})(?=$|[^0-9.]|\\.(?![0-9]))"

    /**
     * Hard cap on the input passed to the regex matcher, in
     * characters. Bodies past this length are truncated up-front so
     * a malicious push cannot widen the matcher's search space
     * arbitrarily. The longest legitimate notification body Android
     * delivers is the SMS Retriever 150-byte payload plus the "big
     * text" extras; 8 192 characters covers every real-world push
     * with comfortable headroom.
     */
    internal const val MAX_INPUT_CHARS: Int = 8 * 1024

    /** Returns the extracted OTP, or `null` if none should be shown. */
    fun extract(ctx: Context, text: String?): String? {
        if (text.isNullOrEmpty()) return null
        val settings = snapshot(ctx)
        return RegexTimeout.run({
            OtpExtractorCore.extract(text, settings)
        }, RegexTimeout.BUDGET_MS)
    }

    /** Forwarded to [OtpTriggers.hasOtpKeyword]. */
    fun hasOtpKeyword(ctx: Context, text: String?): Boolean = OtpTriggers.hasOtpKeyword(ctx, text)

    /** Forwarded to [OtpDiagnoser.diagnose]. */
    fun diagnose(ctx: Context, text: String?): String = OtpDiagnoser.diagnose(ctx, text)

    /**
     * Build the immutable settings snapshot the core needs. Reading
     * each preference here once means the core sees a consistent
     * view even if the user is mid-edit in Settings.
     */
    private fun snapshot(ctx: Context): OtpExtractorSettings = OtpExtractorSettings(
        triggerWords = Prefs.getTriggerWords(ctx),
        stopWords = Prefs.getStopWords(ctx),
        stopWordsEnabled = Prefs.isStopWordsEnabled(ctx),
        ignoredPhrases = Prefs.getIgnoredPhrases(ctx),
        ignoreEnabled = Prefs.isIgnoreEnabled(ctx),
        cleanupPhrases = Prefs.getCleanupPhrases(ctx),
        cleanupEnabled = Prefs.isCleanupEnabled(ctx),
        regex = Prefs.getRegex(ctx),
        currencyTokens = Prefs.getCurrencyTokens(ctx),
        currencySkipEnabled = Prefs.isCurrencySkipEnabled(ctx),
        normalizeDigits = Prefs.isNormalizeDigits(ctx),
    )
}
