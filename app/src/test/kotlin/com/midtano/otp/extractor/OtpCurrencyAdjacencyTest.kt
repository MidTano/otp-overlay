// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct tests for [OtpExtractorCore.isCurrencyAdjacent] — the
 * gate used by the OTP scoring loop to drop digit runs that sit
 * next to a currency token.
 *
 * The function decides whether to reject candidate digits as part
 * of a monetary amount, so a regression here either lets through
 * ATM-receipt amounts or, more dangerously, drops legitimate OTPs
 * that happen to sit near the word "code" in a bilingual body.
 */
class OtpCurrencyAdjacencyTest {

    private val tokens = listOf("USD", "EUR", "RUB", "BYN", "руб", "$", "€", "р.")

    @Test
    fun digitsImmediatelyBeforeCurrencyAreFlagged() {
        val text = "purchase 12345 USD processed"
        val start = text.indexOf("12345")
        val end = start + "12345".length
        assertTrue(OtpExtractorCore.isCurrencyAdjacent(text, start, end, tokens))
    }

    @Test
    fun digitsImmediatelyAfterCurrencyAreFlagged() {
        val text = "balance: $9988 left"
        val start = text.indexOf("9988")
        val end = start + "9988".length
        assertTrue(OtpExtractorCore.isCurrencyAdjacent(text, start, end, tokens))
    }

    @Test
    fun farAwayCurrencyDoesNotPolluteOtp() {
        // The window is 20 chars on each side. Push the currency
        // well past that and the OTP must not be flagged.
        val text = "Your code is 482915 (and unrelated balance is 4 USD long while elsewhere)"
        val start = text.indexOf("482915")
        val end = start + "482915".length
        assertFalse(OtpExtractorCore.isCurrencyAdjacent(text, start, end, tokens))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val text = "amount 12345 usd was charged"
        val start = text.indexOf("12345")
        val end = start + "12345".length
        assertTrue(OtpExtractorCore.isCurrencyAdjacent(text, start, end, tokens))
    }

    @Test
    fun cyrillicCurrencyTokenMatches() {
        val text = "списано 12345 руб с карты"
        val start = text.indexOf("12345")
        val end = start + "12345".length
        assertTrue(OtpExtractorCore.isCurrencyAdjacent(text, start, end, tokens))
    }

    @Test
    fun emptyTokenListShortCircuitsToFalse() {
        val text = "anything 12345 USD"
        val start = text.indexOf("12345")
        val end = start + "12345".length
        assertFalse(OtpExtractorCore.isCurrencyAdjacent(text, start, end, emptyList()))
    }

    @Test
    fun emptyTokenStringIgnored() {
        // A blank entry in the tokens list must not match every body.
        val text = "Your code is 482915"
        val start = text.indexOf("482915")
        val end = start + "482915".length
        assertFalse(OtpExtractorCore.isCurrencyAdjacent(text, start, end, listOf("")))
    }

    @Test
    fun symbolToken_dollar_matchesAdjacent() {
        val text = "tax 12345$ deducted"
        val start = text.indexOf("12345")
        val end = start + "12345".length
        assertTrue(OtpExtractorCore.isCurrencyAdjacent(text, start, end, tokens))
    }

    @Test
    fun windowBoundaryIsExclusiveForOuterEdge() {
        // Build a body where the currency token sits beyond the
        // 20-char left window. Layout is:
        //   "USD"          — 3 chars (token at [0..3))
        //   " " * 25       — 25 spaces of padding
        //   "code 482915"  — OTP starts at index 33
        // window left edge = start - 20 = 13, so the substring scanned
        // is text[13..end+20]. The token at [0..3) is outside, so
        // the gate must NOT flag.
        val text = "USD" + " ".repeat(25) + "code 482915"
        val start = text.indexOf("482915")
        val end = start + "482915".length
        assertFalse(
            "currency token outside the window must not be flagged",
            OtpExtractorCore.isCurrencyAdjacent(text, start, end, tokens),
        )
    }

    @Test
    fun windowBoundaryIsInclusiveForInnerEdge() {
        // Token within the 20-char left window. Layout:
        //   "USD"          — 3 chars (token at [0..3))
        //   " " * 5        — 5 spaces of padding
        //   "code 482915"  — OTP starts at index 13
        // window left edge = start - 20 = -7 → clamp to 0, so the
        // substring scanned is text[0..end+20] which includes "USD".
        val text = "USD" + " ".repeat(5) + "code 482915"
        val start = text.indexOf("482915")
        val end = start + "482915".length
        assertTrue(
            "currency token within the window must be flagged",
            OtpExtractorCore.isCurrencyAdjacent(text, start, end, tokens),
        )
    }
}
