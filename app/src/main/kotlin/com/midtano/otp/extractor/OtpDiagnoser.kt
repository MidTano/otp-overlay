// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import android.content.Context
import com.midtano.otp.R
import com.midtano.otp.data.Prefs

/**
 * Human-readable diagnostic for the Settings → "Test extraction"
 * panel. Lets the user paste a notification body and see exactly what
 * [OtpExtractor] would decide, step by step:
 *
 * 1. Hard-ignore phrase match (and which phrase).
 * 2. Pre-extraction cleanup result.
 * 3. Trigger keyword found / not found.
 * 4. Stop-word match.
 * 5. Configured regex source, match result and currency-skip verdict.
 *
 * Returned text is multi-line and localised through `R.string.diag_*`.
 */
internal object OtpDiagnoser {

    fun diagnose(ctx: Context, text: String?): String {
        if (text.isNullOrEmpty()) return ctx.getString(R.string.diag_empty_text)

        val sb = StringBuilder()
        var body = text
        if (Prefs.isNormalizeDigits(ctx)) {
            body = OtpDigits.normalize(body) ?: body
        }
        sb.append(
            ctx.resources.getQuantityString(
                R.plurals.diag_text_length_plural,
                body.length,
                body.length,
            ),
        )

        val ignoreHit = OtpIgnore.firstHitInfo(ctx, body)
        if (ignoreHit[0] >= 0) {
            val phrases = Prefs.getIgnoredPhrases(ctx)
            val matched = if (ignoreHit[1] in phrases.indices) phrases[ignoreHit[1]] else "?"
            sb.append(ctx.getString(R.string.diag_ignore_blocked, matched, ignoreHit[0]))
            return sb.toString()
        }

        val working: String = OtpCleanup.apply(ctx, body)?.takeIf { it.isNotEmpty() } ?: body
        if (Prefs.isCleanupEnabled(ctx) && working != body) {
            sb.append(
                ctx.resources.getQuantityString(
                    R.plurals.diag_cleanup_applied_plural,
                    working.length,
                    working.length,
                ),
            )
        }

        val words = Prefs.getTriggerWords(ctx)
        val hit = OtpTriggers.firstTriggerHitInfo(ctx, working)
        if (hit[0] >= 0) {
            val matched = if (hit[1] in words.indices) words[hit[1]] else "?"
            sb.append(ctx.getString(R.string.diag_trigger_found, matched, hit[0]))
        } else {
            sb.append(ctx.getString(R.string.diag_trigger_not_found))
            sb.append(ctx.getString(R.string.diag_trigger_list))
            words.forEachIndexed { i, w ->
                if (i > 0) sb.append(", ")
                sb.append(w)
            }
            sb.append('\n')
            return sb.toString()
        }

        if (Prefs.isStopWordsEnabled(ctx)) {
            val stopHit = OtpTriggers.firstStopHitInfo(ctx, working)
            if (stopHit[0] >= 0) {
                val stops = Prefs.getStopWords(ctx)
                val matchedStop = if (stopHit[1] in stops.indices) stops[stopHit[1]] else "?"
                sb.append(ctx.getString(R.string.diag_stop_blocked, matchedStop, stopHit[0]))
                return sb.toString()
            }
        }

        val userRegex = Prefs.getRegex(ctx)
        sb.append(ctx.getString(R.string.diag_regex_label)).append(userRegex).append('\n')
        val p = OtpRegexCache.safeCompile(userRegex)
        if (p == null) {
            sb.append(ctx.getString(R.string.diag_regex_invalid))
            return sb.toString()
        }
        try {
            val m = p.matcher(working)
            if (m.find()) {
                val value = if (m.groupCount() >= 1) m.group(1) else m.group()
                val start = if (m.groupCount() >= 1) m.start(1) else m.start()
                val end = if (m.groupCount() >= 1) m.end(1) else m.end()
                val currencyTokens = if (Prefs.isCurrencySkipEnabled(ctx)) {
                    Prefs.getCurrencyTokens(ctx)
                } else {
                    emptyList()
                }
                if (OtpExtractorCore.isCurrencyAdjacent(working, start, end, currencyTokens)) {
                    sb.append(ctx.getString(R.string.diag_currency_blocked, value, start))
                } else {
                    sb.append(ctx.getString(R.string.diag_code_found, value, start))
                }
            } else {
                sb.append(ctx.getString(R.string.diag_code_not_found))
            }
        } catch (t: Exception) {
            sb.append(ctx.getString(R.string.diag_search_error))
                .append(t.javaClass.simpleName).append(": ").append(t.message)
        }
        return sb.toString()
    }
}
