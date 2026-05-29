// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the standalone primitives in [OtpTriggers] that do not
 * touch a Context: the word-boundary check and the lower-cased phrase
 * scanner. These cover the bug class that used to surface OTPs out of
 * "vscode", "barcode" and "discount code" — the boundary semantics
 * are subtle enough to warrant explicit, deterministic regression
 * coverage.
 */
class OtpTriggersBoundaryTest {

    @Test
    fun isBoundaryOutsideStringIsTrue() {
        assertTrue(OtpTriggers.isBoundary("hello", -1))
        assertTrue(OtpTriggers.isBoundary("hello", 5))
        assertTrue(OtpTriggers.isBoundary("hello", 100))
    }

    @Test
    fun isBoundaryNonLetterIsTrue() {
        assertTrue(OtpTriggers.isBoundary("a 1", 1)) // space
        assertTrue(OtpTriggers.isBoundary("a-b", 1)) // hyphen
        assertTrue(OtpTriggers.isBoundary("a1", 1)) // digit
    }

    @Test
    fun isBoundaryLetterIsFalse() {
        assertFalse(OtpTriggers.isBoundary("ab", 1))
        assertFalse(OtpTriggers.isBoundary("ABC", 1))
        // Cyrillic letters too — Locale-ROOT-aware boundary check
        // must treat them as letters.
        assertFalse(OtpTriggers.isBoundary("код", 1))
    }

    @Test
    fun firstHitInfoEmptyInputs() {
        assertEquals(-1, OtpTriggers.firstHitInfo(null, listOf("code"))[0])
        assertEquals(-1, OtpTriggers.firstHitInfo("", listOf("code"))[0])
        assertEquals(-1, OtpTriggers.firstHitInfo("hello", null)[0])
        assertEquals(-1, OtpTriggers.firstHitInfo("hello", emptyList())[0])
    }

    @Test
    fun asciiKeywordRequiresStrictBoundaries() {
        // "code" inside "vscode" / "barcode" / "encode" must NOT match.
        assertEquals(-1, OtpTriggers.firstHitInfo("vscode is great", listOf("code"))[0])
        assertEquals(-1, OtpTriggers.firstHitInfo("barcode scanner", listOf("code"))[0])
        assertEquals(-1, OtpTriggers.firstHitInfo("please encode", listOf("code"))[0])
    }

    @Test
    fun asciiKeywordMatchesStandalone() {
        val info = OtpTriggers.firstHitInfo("your code is 1234", listOf("code"))
        assertEquals(5, info[0])
        assertEquals(0, info[1])
    }

    @Test
    fun nonAsciiKeywordAcceptsRightSideContinuation() {
        // Stem "одноразов" must match инфлексии: "одноразовый".
        val info = OtpTriggers.firstHitInfo("это одноразовый пароль", listOf("одноразов"))
        assertNotEquals(-1, info[0])
        assertTrue(info[0] >= 0)
    }

    @Test
    fun pickEarliestWhenMultipleMatch() {
        val info = OtpTriggers.firstHitInfo(
            "this otp text mentions code further on",
            listOf("code", "otp"),
        )
        // "otp" hits first at offset 5; "code" comes later.
        assertEquals(5, info[0])
        assertEquals(1, info[1])
    }

    @Test
    fun multipleKeywordsScanAcrossList() {
        val info = OtpTriggers.firstHitInfo("auth code: 9999", listOf("verify", "code"))
        // "code" hits at index 1 in the keyword list.
        assertEquals(1, info[1])
    }
}
