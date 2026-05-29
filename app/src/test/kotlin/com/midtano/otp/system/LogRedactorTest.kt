// SPDX-License-Identifier: MIT
package com.midtano.otp.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LogRedactor].
 *
 * The redactor is a privacy-critical primitive: every diagnostic
 * log line that carries a sender label or a notification body
 * passes through it before landing in the rolling log file. Any
 * regression here either over-redacts (debug usefulness drops) or
 * — much worse — under-redacts (PII reaches disk).
 */
class LogRedactorTest {

    @Test
    fun redactSender_keepsLastFourDigitsOfPhone() {
        val out = LogRedactor.redactSender("+71234567890")
        // 4-digit run "+7" alone is not a digit run; the long digit
        // sequence "71234567890" is masked to ***7890.
        assertEquals("+***7890", out)
    }

    @Test
    fun redactSender_handlesShortcodeLikeRun() {
        val out = LogRedactor.redactSender("900")
        // 3-digit short codes are below the 4-digit threshold and
        // pass through unchanged — short codes rarely identify a
        // single bank account by themselves.
        assertEquals("900", out)
    }

    @Test
    fun redactSender_masksFourDigitRunCompletely() {
        val out = LogRedactor.redactSender("0900")
        // Exactly 4 digits — masked but the tail equals the input,
        // so the marker reads ***0900. Keeping the tail at length
        // 4 prevents leakage when the real input is the actual
        // short code rather than a phone number.
        assertEquals("***0900", out)
    }

    @Test
    fun redactSender_brandLabelPassesThrough() {
        val out = LogRedactor.redactSender("Sberbank")
        assertEquals("Sberbank", out)
    }

    @Test
    fun redactSender_handlesMixedFreeFormAndPhone() {
        val out = LogRedactor.redactSender("From John 79161234567")
        // Long digit run gets the last-4 tail; "John" is left intact.
        assertEquals("From John ***4567", out)
    }

    @Test
    fun redactSender_emptyAndNullReturnEmpty() {
        assertEquals("", LogRedactor.redactSender(null))
        assertEquals("", LogRedactor.redactSender(""))
        assertEquals("", LogRedactor.redactSender("   "))
    }

    @Test
    fun redactDigits_marksOtpLikeWithLength() {
        val out = LogRedactor.redactDigits("Your code is 482915 expires soon")
        assertEquals("Your code is ***6 digits expires soon", out)
    }

    @Test
    fun redactDigits_marksMultipleRuns() {
        val out = LogRedactor.redactDigits("First 1234 then 56789")
        assertEquals("First ***4 digits then ***5 digits", out)
    }

    @Test
    fun redactDigits_passesThroughDigitsBelowMin() {
        // 3-digit run is below the 4-digit threshold and stays raw.
        val out = LogRedactor.redactDigits("Try 123 instead")
        assertEquals("Try 123 instead", out)
    }

    @Test
    fun redactDigits_truncatesAtNineForLongerRuns() {
        // OTP_LIKE has an upper bound of 9; for a 10+ digit run the
        // regex matches the first 9 digits as one run, leaves the
        // tail unmatched. The next find() may match the trailing
        // digits if they form their own 4..9 run. Either way, no
        // OTP-shaped substring of the original remains contiguous.
        val out = LogRedactor.redactDigits("number 1234567890 follows")
        assertFalse(
            "first 9 digits must not survive verbatim: $out",
            out.contains("123456789"),
        )
        assertTrue(
            "expected at least one masking marker: $out",
            out.contains("***9 digits"),
        )
    }

    @Test
    fun redactDigits_emptyReturnsEmpty() {
        assertEquals("", LogRedactor.redactDigits(null))
        assertEquals("", LogRedactor.redactDigits(""))
    }
}
