// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import com.midtano.otp.system.CrashLogger
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/**
 * Memoising compiler for the OTP regex.
 *
 * The notification listener can fire dozens of times in a burst
 * (group summaries plus per-line refreshes), so memoising the compile
 * by source string keeps the hot path allocation-free until the user
 * actually edits the regex from Settings.
 *
 * On a compile failure the cache falls back to
 * [OtpExtractor.DEFAULT_REGEX] so a malformed user-supplied pattern
 * cannot disable extraction outright.
 */
internal object OtpRegexCache {

    /**
     * Atomic source/pattern pair so a reader never observes a stale
     * source paired with a fresh pattern (or vice versa). The
     * earlier two-`@Volatile`-fields layout allowed exactly that
     * race because the two reads were not coupled.
     */
    private data class Entry(val src: String, val pattern: Pattern)

    private val cache = AtomicReference<Entry?>(null)

    /**
     * Compile [regex] (or [OtpExtractor.DEFAULT_REGEX] when blank),
     * memoising the result. Returns `null` only if both the user
     * pattern and the default fail to compile.
     */
    fun safeCompile(regex: String?): Pattern? {
        val src = if (regex.isNullOrEmpty()) OtpExtractor.DEFAULT_REGEX else regex
        cache.get()?.let { snapshot ->
            if (snapshot.src == src) return snapshot.pattern
        }
        return try {
            val p = Pattern.compile(src)
            cache.set(Entry(src, p))
            p
        } catch (e: java.util.regex.PatternSyntaxException) {
            // The Settings editor surfaces compile errors inline; we
            // log here so the rolling diagnostic still shows the
            // failure and we keep extraction alive on the default.
            CrashLogger.logErr(
                "OtpRegexCache: user regex did not compile, falling back to default",
                e,
            )
            if (src != OtpExtractor.DEFAULT_REGEX) {
                try {
                    val p = Pattern.compile(OtpExtractor.DEFAULT_REGEX)
                    cache.set(Entry(OtpExtractor.DEFAULT_REGEX, p))
                    return p
                } catch (e2: java.util.regex.PatternSyntaxException) {
                    // DEFAULT_REGEX is a compile-time constant — reaching
                    // this branch means a broken JVM regex engine.
                    // Returning null disables extraction without
                    // crashing the listener.
                    CrashLogger.logErr("OtpRegexCache: DEFAULT_REGEX failed to compile", e2)
                }
            }
            null
        }
    }
}
