// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import android.content.Context
import com.midtano.otp.data.Prefs
import java.util.Locale

/**
 * Trigger-keyword gate for [OtpExtractor].
 *
 * Many notifications contain numbers that are not OTP codes — IP
 * addresses, postal codes, copyright years, street addresses, phone
 * numbers, etc. To avoid surfacing the overlay for arbitrary numeric
 * content the extractor first asks this helper whether the message
 * even mentions an OTP context word.
 *
 * The gate runs by hand (lowercase the text once, then scan for each
 * keyword) instead of using `Pattern.UNICODE_CHARACTER_CLASS`: that
 * flag relies on ICU tables and has been observed throwing in the
 * static initializer on some Android builds, leaving the class
 * permanently unusable.
 */
internal object OtpTriggers {

    /** Sentinel "no hit" pair, defensively copied on every return. */
    private val MISS = intArrayOf(-1, -1)

    /**
     * `true` if [text] contains any user-configured trigger keyword.
     *
     * Matching semantics depend on the keyword's character set:
     * - ASCII-only keywords (`code`, `otp`, …) require word
     *   boundaries on both sides, so `code` does not match inside
     *   `CodeRabbit`.
     * - Cyrillic / mixed keywords (`код`, `одноразов`, …) only need
     *   a left-side boundary, so stems like `одноразов` match
     *   `одноразовый`, `одноразовая`, etc.
     */
    fun hasOtpKeyword(ctx: Context, text: String?): Boolean = firstTriggerHit(ctx, text) >= 0

    /** @return offset of first trigger word hit, or `-1` if none. */
    fun firstTriggerHit(ctx: Context, text: String?): Int = firstTriggerHitInfo(ctx, text)[0]

    /**
     * Stop-word gate. Returns the offset / index of the first
     * user-defined stop word, or `[-1, -1]` if none. When this
     * matches the extractor returns `null` and the overlay is
     * suppressed.
     */
    fun firstStopHitInfo(ctx: Context, text: String?): IntArray {
        if (text.isNullOrEmpty()) return MISS.copyOf()
        if (!Prefs.isStopWordsEnabled(ctx)) return MISS.copyOf()
        val low = try {
            // Locale.ROOT — see firstTriggerHitInfo.
            text.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            return MISS.copyOf()
        }
        return firstHitInfo(low, Prefs.getStopWords(ctx))
    }

    /**
     * @return `[offset, keywordIndex]` of the first trigger hit, or
     *         `[-1, -1]` if none.
     */
    fun firstTriggerHitInfo(ctx: Context, text: String?): IntArray {
        if (text.isNullOrEmpty()) return MISS.copyOf()
        val low = try {
            // Locale.ROOT — on a Turkish device the default locale's
            // toLowerCase() turns "I" into a dotless "ı" and breaks
            // every keyword that contained 'i'.
            text.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            return MISS.copyOf()
        }
        return firstHitInfo(low, Prefs.getTriggerWords(ctx))
    }

    /**
     * Find the first occurrence of any keyword from [keywords] in
     * [lowercased]. ASCII keywords are bounded on both sides;
     * non-ASCII keywords only need a left-side boundary.
     *
     * @return `[offset, keywordIndex]`, or `[-1, -1]` if none.
     */
    fun firstHitInfo(lowercased: String?, keywords: List<String>?): IntArray {
        if (lowercased.isNullOrEmpty() || keywords.isNullOrEmpty()) return MISS.copyOf()
        var earliest = Int.MAX_VALUE
        var earliestIdx = -1
        for ((i, kw) in keywords.withIndex()) {
            if (kw.isEmpty()) continue
            val strictRight = isAsciiOnly(kw)
            var from = 0
            while (true) {
                val idx = lowercased.indexOf(kw, from)
                if (idx < 0) break
                val leftOk = isBoundary(lowercased, idx - 1)
                val rightOk = !strictRight || isBoundary(lowercased, idx + kw.length)
                if (leftOk && rightOk) {
                    if (idx < earliest) {
                        earliest = idx
                        earliestIdx = i
                    }
                    break
                }
                from = idx + 1
            }
        }
        return if (earliestIdx < 0) MISS.copyOf() else intArrayOf(earliest, earliestIdx)
    }

    /** True if every character in [s] is below code point 0x80. */
    private fun isAsciiOnly(s: String): Boolean {
        for (c in s) if (c.code > 0x7F) return false
        return true
    }

    /**
     * Word-boundary check. A position outside the string counts as a
     * boundary; an in-string position counts as a boundary when the
     * character is not a letter. Digits and underscores are
     * intentionally allowed because trigger words rarely butt up
     * against those in real prose.
     */
    internal fun isBoundary(s: String, pos: Int): Boolean {
        if (pos < 0 || pos >= s.length) return true
        return !s[pos].isLetter()
    }
}
