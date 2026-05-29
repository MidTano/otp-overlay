// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import java.util.regex.Pattern

/**
 * Pure-function cleanup engine, factored out of [OtpCleanup] so unit
 * tests can exercise it without an Android Context.
 *
 * Compiles every supplied phrase into a single alternation pattern
 * and replaces every match with the empty string. Phrases that fail
 * to compile are skipped silently so a single bad entry cannot
 * derail the whole pipeline.
 *
 * The compiled pattern is memoised against the joined phrase list,
 * so re-running on subsequent notifications has zero compile cost
 * unless the user edited the list.
 */
internal object OtpCleanupCore {

    @Volatile private var cachedKey: String? = null

    @Volatile private var cachedPattern: Pattern? = null

    /**
     * Strip every cleanup phrase from [text]. When [enabled] is
     * `false` or [phrases] is empty, the input is returned
     * unchanged.
     */
    fun apply(text: String, enabled: Boolean, phrases: List<String>): String {
        if (text.isEmpty() || !enabled) return text
        val p = compile(phrases) ?: return text
        return try {
            p.matcher(text).replaceAll("")
        } catch (_: IndexOutOfBoundsException) {
            // Java's Matcher can throw IOOBE on some pathological backref
            // patterns even after a successful compile. Falling back to the
            // raw input keeps extraction alive.
            text
        }
    }

    private fun compile(phrases: List<String>): Pattern? {
        if (phrases.isEmpty()) return null
        val key = phrases.joinToString("\n", postfix = "\n")
        cachedPattern?.let { existing ->
            if (key == cachedKey) return existing
        }
        val body = StringBuilder("(")
        var first = true
        for (s in phrases) {
            if (s.isEmpty()) continue
            // Reject phrases that don't parse so a single bad entry
            // can't derail the whole pipeline.
            try {
                Pattern.compile(s)
            } catch (_: java.util.regex.PatternSyntaxException) {
                continue
            }
            if (!first) body.append('|')
            body.append(s)
            first = false
        }
        body.append(')')
        if (first) return null
        return try {
            val compiled = Pattern.compile(
                body.toString(),
                Pattern.CASE_INSENSITIVE or Pattern.MULTILINE,
            )
            cachedKey = key
            cachedPattern = compiled
            compiled
        } catch (_: java.util.regex.PatternSyntaxException) {
            null
        }
    }

    /** Drop the memoised pattern so the next call re-compiles. */
    internal fun invalidate() {
        cachedKey = null
        cachedPattern = null
    }
}
