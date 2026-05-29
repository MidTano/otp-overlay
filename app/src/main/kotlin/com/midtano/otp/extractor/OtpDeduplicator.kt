// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import androidx.annotation.VisibleForTesting

/**
 * Cross-source dedup for OTPs seen by [com.midtano.otp.service.SmsReceiver],
 * [com.midtano.otp.service.NotificationListener] and the in-process
 * overlay queue.
 *
 * Tracks up to [MAX_ENTRIES] recently seen codes for [WINDOW_MS], so
 * a sequence such as "SMS A → SMS B → push A" still suppresses the
 * push for A even when B arrived in between. Comparison is purely on
 * content, so an OTP arriving via SMS and an email-app push surfaces
 * only once.
 */
internal object OtpDeduplicator {

    /** How long after first sighting the same code is considered duplicate. */
    private const val WINDOW_MS: Long = 60_000L

    /**
     * Soft cap on remembered OTPs. A burst of legitimate fresh codes
     * within the window will roll the oldest out; 32 covers any
     * realistic real-world scenario.
     */
    private const val MAX_ENTRIES: Int = 32

    /**
     * LRU map keyed by OTP digits with `accessOrder = true`, so a
     * cache hit also bumps the entry to the end and active codes are
     * not evicted by unrelated bursts.
     */
    private val recent = object : java.util.LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>): Boolean = size > MAX_ENTRIES
    }

    /**
     * Record this OTP as just shown. Always call after surfacing the
     * overlay (regardless of source) so future events within the
     * window are suppressed.
     */
    @Synchronized
    fun markShown(otp: String?) {
        if (otp.isNullOrEmpty()) return
        recent[otp] = System.currentTimeMillis()
    }

    /** `true` if [otp] is identical to one already shown in the window. */
    @Synchronized
    fun isDuplicate(otp: String?): Boolean {
        if (otp.isNullOrEmpty()) return false
        val seenAt = recent[otp] ?: return false
        val age = System.currentTimeMillis() - seenAt
        if (age >= WINDOW_MS) {
            recent.remove(otp)
            return false
        }
        return true
    }

    /**
     * Test-only helper. Marks an OTP with an arbitrary timestamp so
     * unit tests can simulate aged entries without sleeping. Visible
     * for tests in the same module; production code must not call
     * this.
     */
    @VisibleForTesting
    @Synchronized
    internal fun markShownAtForTest(otp: String, timestampMillis: Long) {
        recent[otp] = timestampMillis
    }

    /**
     * Test-only helper that drops every entry. Replaces the previous
     * reflection-based clear in [OtpDeduplicatorTest] so the test
     * surface stays in this file rather than leaking into reflection
     * shenanigans elsewhere.
     */
    @VisibleForTesting
    @Synchronized
    internal fun clearForTest() {
        recent.clear()
    }
}
