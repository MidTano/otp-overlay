// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import java.util.Locale
import java.util.regex.Pattern

/**
 * Pure-function core of [OtpExtractor].
 *
 * Drives the entire extraction pipeline against an explicit
 * [OtpExtractorSettings] snapshot, so it can be exercised by JVM
 * unit tests without an Android runtime. The Context-aware
 * [OtpExtractor.extract] reads the user's preferences once and
 * forwards them straight here.
 *
 * Pipeline (top-down, every step short-circuits the rest on hit):
 *  1. truncate to [OtpExtractor.MAX_INPUT_CHARS] characters,
 *  2. ASCII-fold Arabic-Indic / Persian digits when
 *     [OtpExtractorSettings.normalizeDigits] is on,
 *  3. ignore-phrase gate (`null` if any matches),
 *  4. cleanup pass — strip configured noise phrases via
 *     [OtpCleanupCore.apply],
 *  5. trigger-keyword gate — must hit at least one,
 *  6. stop-word gate when enabled,
 *  7. ReDoS-protected regex run picking the match closest to the
 *     trigger keyword, with currency-adjacency rejection,
 *  8. split-code fallback for `123 456` / `12-34-56` shapes the
 *     main regex deliberately ignores.
 */
internal object OtpExtractorCore {

    /**
     * Penalty added when a regex hit sits BEFORE the trigger keyword.
     * 100 000 is well past any plausible body length and comfortably
     * inside `Int` range, so any forward-distance match wins.
     */
    const val BEHIND_TRIGGER_PENALTY: Int = 100_000

    /** Inclusive bounds of the OTP-likely digit-run length. */
    private const val MIN_OTP_LEN: Int = 4
    private const val MAX_OTP_LEN: Int = 9

    /**
     * Half-window (in characters) the currency filter looks around
     * a candidate digit run.
     */
    private const val CURRENCY_WINDOW: Int = 20

    /**
     * Run the full pipeline against [text] using [settings].
     * Returns the OTP or `null`.
     */
    fun extract(text: String, settings: OtpExtractorSettings): String? {
        if (text.isEmpty()) return null

        var body = if (text.length > OtpExtractor.MAX_INPUT_CHARS) {
            text.substring(0, OtpExtractor.MAX_INPUT_CHARS)
        } else {
            text
        }

        if (settings.normalizeDigits) {
            body = OtpDigits.normalize(body) ?: body
        }

        if (settings.ignoreEnabled && hasAnyKeyword(body, settings.ignoredPhrases)) return null

        val cleaned = OtpCleanupCore.apply(body, settings.cleanupEnabled, settings.cleanupPhrases)
        val working = if (cleaned.isNotEmpty()) cleaned else body

        return runRegexPipeline(working, settings)
    }

    /**
     * Trigger / stop / regex / split-code stages — split out so the
     * settings-bound entry point can keep its own surface tidy.
     */
    fun runRegexPipeline(working: String, settings: OtpExtractorSettings): String? {
        if (working.isEmpty()) return null
        val lowered = working.lowercase(Locale.ROOT)

        val triggerPos = OtpTriggers.firstHitInfo(lowered, settings.triggerWords)[0]
        if (triggerPos < 0) return null

        if (settings.stopWordsEnabled &&
            OtpTriggers.firstHitInfo(lowered, settings.stopWords)[0] >= 0
        ) {
            return null
        }

        val pattern = compileOrFallback(settings.regex)
        if (pattern != null) {
            val best = bestRegexMatch(working, pattern, settings, triggerPos)
            if (best != null) return best
        }

        return tryJoinedSplitCode(working)
    }

    /** Compile [src], falling back to [OtpExtractor.DEFAULT_REGEX] on failure. */
    private fun compileOrFallback(src: String): Pattern? {
        return try {
            Pattern.compile(src.ifEmpty { OtpExtractor.DEFAULT_REGEX })
        } catch (_: java.util.regex.PatternSyntaxException) {
            try {
                Pattern.compile(OtpExtractor.DEFAULT_REGEX)
            } catch (_: java.util.regex.PatternSyntaxException) {
                // DEFAULT_REGEX is a compile-time constant — reaching this
                // branch means a broken JVM regex engine. Returning null
                // disables extraction without crashing the listener.
                null
            }
        }
    }

    /**
     * Walk every regex hit and pick the one closest to [triggerPos].
     * Letter-adjacent matches and currency-adjacent runs are skipped.
     */
    private fun bestRegexMatch(
        working: String,
        pattern: Pattern,
        settings: OtpExtractorSettings,
        triggerPos: Int,
    ): String? {
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        val matcher = pattern.matcher(InterruptibleCharSequence(working))
        while (matcher.find()) {
            val start = if (matcher.groupCount() >= 1) matcher.start(1) else matcher.start()
            val end = if (matcher.groupCount() >= 1) matcher.end(1) else matcher.end()
            val value = if (matcher.groupCount() >= 1) matcher.group(1) else matcher.group()
            if (value == null) continue
            if (isLetterAt(working, start - 1) || isLetterAt(working, end)) continue
            if (settings.currencySkipEnabled &&
                isCurrencyAdjacent(working, start, end, settings.currencyTokens)
            ) {
                continue
            }
            val distance = start - triggerPos
            val score = if (distance >= 0) distance else (-distance + BEHIND_TRIGGER_PENALTY)
            if (score < bestScore) {
                bestScore = score
                best = value
            }
        }
        return best
    }

    private fun isLetterAt(s: String, pos: Int): Boolean =
        pos in s.indices && s[pos].isLetter()

    /**
     * Currency-adjacency gate.
     *
     * Drops digit runs that sit within [CURRENCY_WINDOW] characters
     * of any token from [tokens] (case-insensitive, `Locale.ROOT`-folded
     * so Turkish 'I' / 'ı' folding cannot break "USD"/"RUB" matches in
     * localised banking notifications). Empty [tokens] disables the
     * gate.
     */
    fun isCurrencyAdjacent(
        text: String,
        start: Int,
        end: Int,
        tokens: List<String>,
    ): Boolean {
        if (tokens.isEmpty()) return false
        val from = maxOf(0, start - CURRENCY_WINDOW)
        val to = minOf(text.length, end + CURRENCY_WINDOW)
        val window = text.substring(from, to).lowercase(Locale.ROOT)
        for (token in tokens) {
            if (token.isEmpty()) continue
            if (window.contains(token.lowercase(Locale.ROOT))) return true
        }
        return false
    }

    /**
     * `true` if any keyword in [keywords] occurs in [text] under the
     * boundary semantics of [OtpTriggers]. Used by the ignore-phrase
     * gate.
     */
    fun hasAnyKeyword(text: String, keywords: List<String>): Boolean {
        if (text.isEmpty() || keywords.isEmpty()) return false
        return OtpTriggers.firstHitInfo(text.lowercase(Locale.ROOT), keywords)[0] >= 0
    }

    /**
     * Recover OTPs the main regex skips because of internal
     * separators. Two shapes are accepted:
     *
     * ```
     *   AAA[ -]BBB     // 3+3, 3+4, 4+3, 4+4
     *   AA[ -]BB[ -]CC // three pairs
     * ```
     */
    private fun tryJoinedSplitCode(text: String): String? {
        runCatching {
            val m = SPLIT_PAT_2.matcher(text)
            if (m.find()) {
                val a = m.group(1) ?: return null
                val b = m.group(2) ?: return null
                val joined = a + b
                if (joined.length in MIN_OTP_LEN..MAX_OTP_LEN) return joined
            }
        }

        runCatching {
            val m = SPLIT_PAT_3.matcher(text)
            if (m.find()) {
                val a = m.group(1) ?: return null
                val b = m.group(2) ?: return null
                val c = m.group(3) ?: return null
                return a + b + c
            }
        }
        return null
    }

    private val SPLIT_PAT_2: Pattern = Pattern.compile(
        "(?<![\\p{L}\\p{N}.\\-])([0-9]{3,4})[ \\-]([0-9]{3,4})(?![\\p{L}\\p{N}.\\-])",
    )
    private val SPLIT_PAT_3: Pattern = Pattern.compile(
        "(?<![\\p{L}\\p{N}.\\-])([0-9]{2})[ \\-]([0-9]{2})[ \\-]([0-9]{2})(?![\\p{L}\\p{N}.\\-])",
    )
}

/**
 * Snapshot of every preference [OtpExtractorCore] needs to make a
 * decision. The Context-aware [OtpExtractor.extract] builds one of
 * these from the user's prefs and forwards it; tests build the same
 * record by hand.
 */
internal data class OtpExtractorSettings(
    val triggerWords: List<String>,
    val stopWords: List<String>,
    val stopWordsEnabled: Boolean,
    val ignoredPhrases: List<String>,
    val ignoreEnabled: Boolean,
    val cleanupPhrases: List<String>,
    val cleanupEnabled: Boolean,
    val regex: String,
    val currencyTokens: List<String>,
    val currencySkipEnabled: Boolean,
    val normalizeDigits: Boolean,
)
