// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM regression tests for [OtpExtractorCore]. The core is the
 * settings-driven kernel of the extractor; the Context-bound
 * [OtpExtractor.extract] is a thin wrapper that builds an
 * [OtpExtractorSettings] from the user's preferences and forwards
 * here. By testing the core directly we get coverage of:
 * - the truncate / normalise / ignore / cleanup pre-stages,
 * - trigger-distance scoring,
 * - currency-adjacency rejection,
 * - letter-boundary rejection,
 * - the split-code fallback (2- and 3-group shapes),
 * - malformed user regex falling back to the default,
 * without spinning up an Android runtime.
 */
class OtpExtractorCoreTest {

    private fun defaults(
        triggers: List<String> = listOf("code", "otp", "verification", "одноразов", "код"),
        stops: List<String> = emptyList(),
        stopOn: Boolean = stops.isNotEmpty(),
        ignored: List<String> = emptyList(),
        ignoreOn: Boolean = ignored.isNotEmpty(),
        cleanup: List<String> = emptyList(),
        cleanupOn: Boolean = cleanup.isNotEmpty(),
        regex: String = OtpExtractor.DEFAULT_REGEX,
        currency: List<String> = listOf("USD", "EUR", "RUB", "$", "€"),
        currencyOn: Boolean = true,
        normalizeDigits: Boolean = true,
    ): OtpExtractorSettings = OtpExtractorSettings(
        triggerWords = triggers,
        stopWords = stops,
        stopWordsEnabled = stopOn,
        ignoredPhrases = ignored,
        ignoreEnabled = ignoreOn,
        cleanupPhrases = cleanup,
        cleanupEnabled = cleanupOn,
        regex = regex,
        currencyTokens = currency,
        currencySkipEnabled = currencyOn,
        normalizeDigits = normalizeDigits,
    )

    @Test
    fun emptyInputReturnsNull() {
        assertNull(OtpExtractorCore.extract("", defaults()))
    }

    @Test
    fun bodyWithoutTriggerKeywordReturnsNull() {
        assertNull(OtpExtractorCore.extract("Order #12345 has shipped", defaults()))
    }

    @Test
    fun englishCodeBodyExtracts() {
        val body = "Your verification code is 482915. It expires in 5 minutes."
        assertEquals("482915", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun russianCodeBodyExtracts() {
        val body = "Код подтверждения: 731092. Никому не сообщайте."
        assertEquals("731092", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun stopWordSuppressesExtraction() {
        val body = "Регистрация на марафон. Ваш код 123456 ждёт."
        val s = defaults(stops = listOf("марафон"), stopOn = true)
        assertNull(OtpExtractorCore.extract(body, s))
    }

    @Test
    fun ignoredPhraseShortCircuitsBeforeTrigger() {
        val body = "Your vscode update 12345 is ready"
        val s = defaults(ignored = listOf("vscode"), ignoreOn = true)
        assertNull(OtpExtractorCore.extract(body, s))
    }

    @Test
    fun ignoreDisabledLetsCleanCodeThrough() {
        val body = "Your verification code is 482915 (vscode mention later)"
        // Ignore list contains vscode but feature is OFF — the
        // extraction must proceed.
        val s = defaults(ignored = listOf("vscode"), ignoreOn = false)
        assertEquals("482915", OtpExtractorCore.extract(body, s))
    }

    @Test
    fun cleanupStripsDomainsBeforeRegex() {
        // Without cleanup the regex would match "999" inside the
        // domain and reject it via letter-adjacency. With cleanup
        // we get a clean run.
        val body = "From example.com — your code is 482915"
        val s = defaults(
            cleanup = listOf("[a-zA-Z0-9]+\\.com"),
            cleanupOn = true,
        )
        assertEquals("482915", OtpExtractorCore.extract(body, s))
    }

    @Test
    fun closestRunToTriggerWins() {
        val body = "Reference 998877 — your code is 9001 (do not share)."
        assertEquals("9001", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun currencyAdjacentDigitsSkipped() {
        val body = "Your payment of 12345 USD has been processed (code: 9988)"
        assertEquals("9988", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun currencyFilterDisabledStillRejectsByLetterAdjacency() {
        val body = "Your payment of 12345 USD has been processed (code: 9988)"
        assertEquals(
            "9988",
            OtpExtractorCore.extract(body, defaults(currency = emptyList(), currencyOn = false)),
        )
    }

    @Test
    fun letterAdjacentMatchesRejected() {
        val body = "Login id1234abc; please enter your code"
        assertNull(OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun splitCodeFallbackJoinsTwoGroups() {
        val body = "Login code 123 456 expires soon"
        assertEquals("123456", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun splitCodeFallbackJoinsThreeGroups() {
        val body = "Confirmation code 12-34-56 — expires in 5 min"
        assertEquals("123456", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun malformedUserRegexFallsBackToDefault() {
        val body = "verification code 555444 ends"
        val s = defaults(regex = "([a-z+")
        assertNotNull(OtpExtractorCore.extract(body, s))
        assertEquals("555444", OtpExtractorCore.extract(body, s))
    }

    @Test
    fun phoneNumberWithoutKeywordIgnored() {
        assertNull(OtpExtractorCore.extract("Call back +79161234567 for details", defaults()))
    }

    @Test
    fun farRunWinsOverImpossibleClose() {
        val body = "Order 998877 placed. Your code is 909090."
        assertEquals("909090", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun persianDigitsNormalisedAndExtracted() {
        // Persian digits + English keyword. Normalisation rewrites
        // the digit run before the regex sees it.
        val body = "code: \u06F1\u06F2\u06F3\u06F4\u06F5\u06F6"
        assertEquals("123456", OtpExtractorCore.extract(body, defaults()))
    }

    @Test
    fun normalisationOffKeepsScript() {
        // Persian digits without normalisation never match the ASCII
        // 0..9 default regex.
        val body = "code: \u06F1\u06F2\u06F3\u06F4\u06F5\u06F6"
        val s = defaults(normalizeDigits = false)
        assertNull(OtpExtractorCore.extract(body, s))
    }

    @Test
    fun overlongInputIsTruncatedNotCrashed() {
        // Push body well past MAX_INPUT_CHARS — must still return
        // promptly without throwing.
        val tail = "code: 482915 — please use within 5 minutes"
        val padded = "x".repeat(OtpExtractor.MAX_INPUT_CHARS) + tail
        // The keyword sits OUTSIDE the truncation window, so the
        // extractor returns null. The point is: no exception, no
        // matcher hang.
        assertNull(OtpExtractorCore.extract(padded, defaults()))
    }
}
