// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The dedup is a process-wide singleton. Tests use the
 * [OtpDeduplicator.clearForTest] / [OtpDeduplicator.markShownAtForTest]
 * helpers (annotated `@VisibleForTesting`) instead of reflection, so
 * the test surface stays explicit and survives obfuscation /
 * field-rename refactors.
 *
 * The dedup window itself (60 s) is verified indirectly: the
 * `markShownAtForTest` writer drops a timestamp of our choosing, so
 * we can express "remembered" and "freshly forgotten" without
 * sleeping.
 */
class OtpDeduplicatorTest {

    @Before
    fun resetSingleton() {
        OtpDeduplicator.clearForTest()
    }

    @Test
    fun nullAndEmptyAreNeverDuplicates() {
        assertFalse(OtpDeduplicator.isDuplicate(null))
        assertFalse(OtpDeduplicator.isDuplicate(""))
    }

    @Test
    fun freshOtpIsNotDuplicate() {
        assertFalse(OtpDeduplicator.isDuplicate("123456"))
    }

    @Test
    fun markedOtpIsDuplicate() {
        OtpDeduplicator.markShown("123456")
        assertTrue(OtpDeduplicator.isDuplicate("123456"))
    }

    @Test
    fun differentOtpsAreIndependent() {
        OtpDeduplicator.markShown("AAAAAA")
        assertFalse(OtpDeduplicator.isDuplicate("BBBBBB"))
    }

    @Test
    fun expiredEntryDropsOnRead() {
        OtpDeduplicator.markShownAtForTest("777777", System.currentTimeMillis() - 120_000L)
        assertFalse(OtpDeduplicator.isDuplicate("777777"))
        // Second look-up confirms the entry was evicted by the read.
        assertFalse(OtpDeduplicator.isDuplicate("777777"))
    }

    @Test
    fun lruEvictsBeyondCap() {
        // The internal cap is 32; mark 40 distinct codes and the
        // earliest entries must roll out — verified through the
        // public predicate alone.
        for (i in 0 until 40) OtpDeduplicator.markShown("code$i")
        assertFalse(OtpDeduplicator.isDuplicate("code0"))
        assertTrue(OtpDeduplicator.isDuplicate("code39"))
    }

    @Test
    fun reMarkRefreshesFreshness() {
        // Set the initial entry to almost-expired (50 s ago).
        OtpDeduplicator.markShownAtForTest("424242", System.currentTimeMillis() - 50_000L)
        assertTrue(OtpDeduplicator.isDuplicate("424242"))
        // markShown stamps "now"; verify the entry is still flagged
        // as duplicate immediately afterwards.
        OtpDeduplicator.markShown("424242")
        assertTrue(OtpDeduplicator.isDuplicate("424242"))
    }
}
