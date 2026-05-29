// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import android.content.Context
import com.midtano.otp.data.Prefs
import java.util.Locale

/**
 * Hard ignore filter. When any of the configured phrases is present
 * in the message body the extractor short-circuits before trigger,
 * regex or cleanup is applied.
 *
 * Word-boundary semantics match [OtpTriggers]: ASCII-only phrases
 * require word boundaries on both sides; non-ASCII phrases accept a
 * left-side boundary only.
 */
internal object OtpIgnore {

    private val MISS = intArrayOf(-1, -1)

    /** True if the user has enabled ignore phrases AND any phrase matches. */
    fun shouldIgnore(ctx: Context, text: String?): Boolean = firstHitInfo(ctx, text)[0] >= 0

    /**
     * @return `[offset, phraseIndex]` of the first matching ignore
     *         phrase, or `[-1, -1]` if the filter is off or no phrase
     *         matches.
     */
    fun firstHitInfo(ctx: Context, text: String?): IntArray {
        if (text.isNullOrEmpty()) return MISS.copyOf()
        if (!Prefs.isIgnoreEnabled(ctx)) return MISS.copyOf()
        val low = try {
            // Locale.ROOT — see OtpTriggers.firstStopHitInfo.
            text.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            return MISS.copyOf()
        }
        val phrases = Prefs.getIgnoredPhrases(ctx)
        if (phrases.isEmpty()) return MISS.copyOf()
        return OtpTriggers.firstHitInfo(low, phrases)
    }
}
