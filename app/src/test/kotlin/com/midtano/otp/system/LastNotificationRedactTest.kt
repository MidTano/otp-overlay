// SPDX-License-Identifier: MIT
package com.midtano.otp.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Privacy-critical tests: the redactor must NEVER let a 4..9 digit
 * run survive a round trip into the diagnostic store, and must fail
 * closed (return a non-empty marker) if its own regex engine breaks.
 *
 * The function is `internal`, but because tests live in the same
 * gradle module we can call it directly without going through
 * reflection.
 */
class LastNotificationRedactTest {

    @Test
    fun nullAndEmptyReturnEmpty() {
        assertEquals("", LastNotification.redact(null))
        assertEquals("", LastNotification.redact(""))
    }

    @Test
    fun shortDigitRunsAreNotRedacted() {
        // 1..3 digits: not in the OTP-likely length window, kept verbatim.
        val result = LastNotification.redact("Pin 1 done, 12 left, 999 to go")
        assertEquals("Pin 1 done, 12 left, 999 to go", result)
    }

    @Test
    fun typicalOtpRunIsMasked() {
        val result = LastNotification.redact("Your code is 123456 — do not share")
        assertFalse("OTP digits leaked into stored body: $result", result.contains("123456"))
        assertTrue(result.contains("***6 digits"))
    }

    @Test
    fun multipleRunsAllMasked() {
        val result = LastNotification.redact("Codes: 1234 and 567890 expire soon")
        assertFalse(result.contains("1234"))
        assertFalse(result.contains("567890"))
        assertTrue(result.contains("***4 digits"))
        assertTrue(result.contains("***6 digits"))
    }

    @Test
    fun longerRunsAlsoMasked() {
        // 7..9 digit codes still fall in the OTP window.
        val result = LastNotification.redact("Authenticator: 12345678 (8 digits)")
        assertFalse(result.contains("12345678"))
        assertTrue(result.contains("***8 digits"))
    }

    @Test
    fun lengthMarkerMatchesActualLength() {
        // Spot-check every length in the masked range.
        for (len in 4..9) {
            val digits = "1".repeat(len)
            val out = LastNotification.redact("Code: $digits end")
            assertTrue("len=$len out=$out", out.contains("***$len digits"))
            assertFalse("len=$len leaked: $out", out.contains(digits))
        }
    }

    @Test
    fun textWithoutDigitsPassesThrough() {
        val msg = "No code here, just prose."
        assertEquals(msg, LastNotification.redact(msg))
    }

    @Test
    fun extremelyLongRunStillMasked() {
        // 10+ digit run gets pre-masked as a phone marker with the
        // run length, then the OTP-window pass cannot re-match the
        // marker because it contains no digits.
        val out = LastNotification.redact("Junk: 12345678901234567890 end")
        assertFalse("phone-like run leaked: $out", out.contains("12345678901234567890"))
        assertTrue("expected length-tagged phone marker in output: $out", out.contains("***20-digit-phone"))
    }

    @Test
    fun phoneLikeNumberIsMaskedWithoutLeakingDigits() {
        // International phone numbers consistently exceed 9 digits.
        // The persisted form preserves the run length but no actual
        // digits — notification bodies persist on disk and the
        // diagnostic panel surfaces them, so even a last-four tail
        // would defeat the redaction here.
        val out = LastNotification.redact("From +71234567890: code 482915")
        assertFalse("phone digits leaked: $out", out.contains("71234567890"))
        assertFalse("OTP leaked: $out", out.contains("482915"))
        assertTrue(out.contains("***11-digit-phone"))
        assertTrue(out.contains("***6 digits"))
    }

    @Test
    fun redactorSurvivesUnicodeBody() {
        // Cyrillic + Arabic text plus a Latin-1 OTP sequence.
        val raw = "Код 555444 одноразовый — \u0633\u0644\u0627\u0645"
        val out = LastNotification.redact(raw)
        assertFalse(out.contains("555444"))
        assertTrue(out.contains("***6 digits"))
        // Non-digit content survives unchanged.
        assertTrue(out.contains("одноразовый"))
    }
}
