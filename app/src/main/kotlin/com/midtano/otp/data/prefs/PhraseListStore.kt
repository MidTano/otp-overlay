// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

import java.util.Locale

/**
 * Helpers for parsing and serialising the newline-joined phrase-list
 * strings that [PrefsFilter] keeps in [PrefsFile].
 *
 * Rules shared by every list-style preference:
 * - empty / blank entries are dropped,
 * - duplicates are collapsed (insertion order preserved),
 * - case folding uses [Locale.ROOT] when [lowercase] is `true`, so
 *   the Turkish-i mapping cannot break ASCII keyword comparisons.
 */
internal object PhraseListStore {

    /**
     * Decode a newline-joined preference value into a list.
     *
     * @param raw       persisted string, or `null` when the user has
     *                  never set the list (caller falls back to its
     *                  built-in defaults).
     * @param lowercase apply [String.lowercase] with [Locale.ROOT];
     *                  used for trigger and stop words. Regex,
     *                  cleanup and currency lists pass `false` to
     *                  preserve symbols like `€` or `RUB` exactly as
     *                  typed.
     */
    fun parse(raw: String?, lowercase: Boolean): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        val out = ArrayList<String>()
        for (line in raw.split('\n')) {
            var t = line.trim()
            if (t.isEmpty()) continue
            if (lowercase) t = t.lowercase(Locale.ROOT)
            if (t !in out) out.add(t)
        }
        return out
    }

    /** Inverse of [parse]: dedupe and join with newlines. */
    fun join(words: List<String>?, lowercase: Boolean): String {
        if (words.isNullOrEmpty()) return ""
        val dedup = LinkedHashSet<String>(words.size)
        for (w in words) {
            var t = w.trim()
            if (t.isEmpty()) continue
            if (lowercase) t = t.lowercase(Locale.ROOT)
            dedup.add(t)
        }
        return dedup.joinToString(separator = "\n")
    }
}
