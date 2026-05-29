// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

/**
 * [CharSequence] wrapper that throws when the calling thread is
 * interrupted, giving Java's regex engine a way out of catastrophic
 * backtracking on a user-supplied pattern.
 *
 * [java.util.regex.Matcher] reads the input one character at a time
 * during backtracking, so checking [Thread.isInterrupted] on every
 * read is the idiomatic way to bound a regex match by wall-clock
 * time. The check is a single static call plus a volatile read; on
 * non-degenerate input it adds single-digit nanoseconds per character
 * and is invisible against the matcher's own work.
 */
internal class InterruptibleCharSequence(
    private val inner: CharSequence,
) : CharSequence {

    override val length: Int get() = inner.length

    override fun get(index: Int): Char {
        if (Thread.currentThread().isInterrupted) {
            // Throwing out of `get` is the only way to break out of
            // Matcher.find() mid-scan; the caller (RegexTimeout.run)
            // catches it.
            throw RegexTimeoutException()
        }
        return inner[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        InterruptibleCharSequence(inner.subSequence(startIndex, endIndex))

    override fun toString(): String = inner.toString()
}
