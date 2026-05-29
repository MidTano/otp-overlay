// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure-JVM tests for the Arabic-Indic / Persian → ASCII normalizer.
 * Real-world arrival paths are covered: messages from MENA banks
 * commonly arrive with digits in U+0660..U+0669 and Persian ones in
 * U+06F0..U+06F9.
 */
class OtpDigitsTest {

    @Test
    fun nullPassesThrough() {
        assertNull(OtpDigits.normalize(null))
    }

    @Test
    fun emptyPassesThrough() {
        assertEquals("", OtpDigits.normalize(""))
    }

    @Test
    fun asciiOnlyReturnsSameInstance() {
        // No allocation when nothing to do — important on the hot path.
        val input = "Your code is 123456"
        assertSame(input, OtpDigits.normalize(input))
    }

    @Test
    fun normalisesArabicIndic() {
        // U+0660..U+0669 → '0'..'9'
        val arabic = "كود التحقق: \u0661\u0662\u0663\u0664\u0665\u0666"
        val out = OtpDigits.normalize(arabic)
        assertEquals("كود التحقق: 123456", out)
    }

    @Test
    fun normalisesPersian() {
        val persian = "کد: \u06F1\u06F2\u06F3\u06F4\u06F5"
        assertEquals("کد: 12345", OtpDigits.normalize(persian))
    }

    @Test
    fun mixedScriptsAllNormalise() {
        // Real-world: a banking SMS that mixes ASCII brand suffix
        // with Arabic-Indic digit body.
        val mixed = "Bank \u0669\u0668\u0667 / 100 USD"
        assertEquals("Bank 987 / 100 USD", OtpDigits.normalize(mixed))
    }

    @Test
    fun nonDigitUnicodeUntouched() {
        val input = "Naïve résumé café 2024"
        assertSame(input, OtpDigits.normalize(input))
    }
}
