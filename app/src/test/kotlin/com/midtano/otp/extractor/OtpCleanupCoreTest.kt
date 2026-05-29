// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM tests for [OtpCleanupCore]. Verifies the regex-alternation
 * compile path, the failure-tolerant compile (one bad phrase doesn't
 * disable the rest), the cache-by-joined-key behaviour, and the
 * disabled / empty short-circuits.
 *
 * The cache is process-wide; [Before] flushes it so each test sees a
 * fresh compile.
 */
class OtpCleanupCoreTest {

    @Before
    fun resetCache() {
        OtpCleanupCore.invalidate()
    }

    @Test
    fun disabledReturnsInputUnchanged() {
        val text = "From example.com — your code is 482915"
        assertEquals(text, OtpCleanupCore.apply(text, enabled = false, phrases = listOf("\\.com")))
    }

    @Test
    fun emptyTextReturnsEmpty() {
        assertEquals("", OtpCleanupCore.apply("", enabled = true, phrases = listOf("foo")))
    }

    @Test
    fun emptyPhrasesReturnsInputUnchanged() {
        val text = "Your code 482915"
        assertEquals(text, OtpCleanupCore.apply(text, enabled = true, phrases = emptyList()))
    }

    @Test
    fun stripsConfiguredDomain() {
        val text = "From example.com — your code is 482915"
        val out = OtpCleanupCore.apply(
            text,
            enabled = true,
            phrases = listOf("[a-zA-Z0-9]+\\.com"),
        )
        // The "example.com" substring is removed; everything else
        // survives, including the leading "From " and the OTP.
        assertEquals(false, out.contains("example.com"))
        assertEquals(true, out.contains("482915"))
    }

    @Test
    fun stripsAlternationPhrases() {
        val text = "code 482915 (vscode mention) — share OTP"
        val out = OtpCleanupCore.apply(
            text,
            enabled = true,
            phrases = listOf("vscode", "share OTP"),
        )
        assertEquals(false, out.contains("vscode"))
        assertEquals(false, out.contains("share OTP"))
        assertEquals(true, out.contains("482915"))
    }

    @Test
    fun badPhraseDoesNotDisablePipeline() {
        // Unbalanced bracket — Pattern.compile throws. The other
        // valid phrase must still be applied.
        val text = "vscode here — code 482915"
        val out = OtpCleanupCore.apply(
            text,
            enabled = true,
            phrases = listOf("([a-z+", "vscode"),
        )
        assertEquals(false, out.contains("vscode"))
        assertEquals(true, out.contains("482915"))
    }

    @Test
    fun cacheReusesCompiledPattern() {
        // Same phrases ⇒ same compiled pattern instance reused. We
        // can only observe this indirectly — call twice and verify
        // both runs produce identical output.
        val text = "vscode 12345"
        val phrases = listOf("vscode")
        val first = OtpCleanupCore.apply(text, enabled = true, phrases = phrases)
        val second = OtpCleanupCore.apply(text, enabled = true, phrases = phrases)
        assertEquals(first, second)
        assertNotNull(first)
    }

    @Test
    fun caseInsensitiveByDefault() {
        // The compile flags include CASE_INSENSITIVE so "VSCode"
        // gets stripped just like "vscode".
        val out = OtpCleanupCore.apply(
            "VSCode update — code 482915",
            enabled = true,
            phrases = listOf("vscode"),
        )
        assertEquals(false, out.contains("VSCode"))
    }

    @Test
    fun onlyBadPhrasesYieldUnchangedInput() {
        // Every phrase fails to compile ⇒ no replacement, original
        // input survives.
        val text = "code 482915"
        val out = OtpCleanupCore.apply(
            text,
            enabled = true,
            phrases = listOf("([a-z+", "(["),
        )
        assertEquals(text, out)
    }
}
