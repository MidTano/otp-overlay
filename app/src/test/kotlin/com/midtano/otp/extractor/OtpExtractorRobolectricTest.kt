// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midtano.otp.data.Prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric integration tests for [OtpExtractor].
 *
 * Pure-function unit tests live in `OtpExtractorCoreTest`; this
 * suite covers the Context-aware code path that loads preferences
 * via `Prefs.*` and routes them into `OtpExtractorCore.extract`.
 *
 * Each test resets the prefs file in [setUp] / [tearDown] so prefs
 * mutations from one test cannot poison the next.
 */
@RunWith(AndroidJUnit4::class)
class OtpExtractorRobolectricTest {

    private val ctx
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        Prefs.resetAllExceptOnboarding(ctx)
        OtpDeduplicator.markShownAtForTest("__warmup__", -10_000_000L)
        OtpDeduplicator.clearForTest()
    }

    @After
    fun tearDown() {
        Prefs.resetAllExceptOnboarding(ctx)
        OtpDeduplicator.clearForTest()
    }

    @Test
    fun extractsBasicOtpFromTriggerSentence() {
        val out = OtpExtractor.extract(ctx, "Your verification code is 482915")
        assertEquals("482915", out)
    }

    @Test
    fun extractsRussianOtpAfterCyrillicTrigger() {
        val out = OtpExtractor.extract(ctx, "Ваш одноразовый код: 384712")
        assertEquals("384712", out)
    }

    @Test
    fun returnsNullWithoutTriggerKeyword() {
        // No trigger word in either language; even if a digit run
        // is present the extractor must abstain.
        assertNull(OtpExtractor.extract(ctx, "Your appointment is at 12:34 on 482915 Smith Street"))
    }

    @Test
    fun returnsNullForEmptyBody() {
        assertNull(OtpExtractor.extract(ctx, ""))
    }

    @Test
    fun returnsNullForNullInput() {
        assertNull(OtpExtractor.extract(ctx, null))
    }

    @Test
    fun honoursStopWordsWhenEnabled() {
        Prefs.setStopWordsEnabled(ctx, true)
        Prefs.setStopWords(ctx, listOf("payment"))
        // The body has BOTH a trigger ("verification code") AND a
        // stop word ("payment"). With stop words on, the extractor
        // returns null.
        assertNull(
            OtpExtractor.extract(
                ctx,
                "Your verification code is 482915 for the payment to merchant",
            ),
        )
    }

    @Test
    fun stopWordsAreInactiveWhenDisabled() {
        Prefs.setStopWordsEnabled(ctx, false)
        Prefs.setStopWords(ctx, listOf("payment"))
        val out = OtpExtractor.extract(
            ctx,
            "Your verification code is 482915 for the payment to merchant",
        )
        assertEquals("482915", out)
    }

    @Test
    fun ignoreListShortCircuitsExtraction() {
        Prefs.setIgnoreEnabled(ctx, true)
        Prefs.setIgnoredPhrases(ctx, listOf("promotional"))
        assertNull(
            OtpExtractor.extract(
                ctx,
                "promotional offer: your verification code is 482915 (this is spam)",
            ),
        )
    }

    @Test
    fun currencyAdjacentDigitsAreRejected() {
        Prefs.setCurrencySkipEnabled(ctx, true)
        Prefs.setCurrencyTokens(ctx, listOf("USD"))
        // The body has a currency-amount candidate ("12345 USD") and
        // a separate, far-away OTP candidate. Distance between the
        // two digit runs is well past the 20-char window so the OTP
        // picker can pick "482915" without the currency window
        // dragging it in too.
        val padding = " filler text ".repeat(8)
        val body = "Your verification code is 482915.$padding Debited 12345 USD from card."
        val out = OtpExtractor.extract(ctx, body)
        assertEquals("482915", out)
    }

    @Test
    fun currencyAdjacentDigitsAreRejectedAndAlternativeStillWins() {
        Prefs.setCurrencySkipEnabled(ctx, true)
        Prefs.setCurrencyTokens(ctx, listOf("USD"))
        // Only the currency-adjacent candidate exists; with the
        // skip on, extraction must abstain rather than picking the
        // amount.
        assertNull(OtpExtractor.extract(ctx, "Your code: charged 12345 USD"))
    }

    @Test
    fun normalizeDigitsConvertsArabicIndicToAscii() {
        Prefs.setNormalizeDigits(ctx, true)
        // Eastern-Arabic numerals 482915 → ASCII before extraction.
        val out = OtpExtractor.extract(ctx, "Your verification code is \u0664\u0668\u0662\u0669\u0661\u0665")
        assertEquals("482915", out)
    }

    @Test
    fun userRegexOverridesDefault() {
        // Set a strict 8-digit-only regex; a 6-digit OTP must miss.
        Prefs.setRegex(ctx, "(\\d{8})")
        assertNull(OtpExtractor.extract(ctx, "Your verification code is 482915"))
        // 8-digit OTP must hit.
        val out = OtpExtractor.extract(ctx, "Your verification code is 48291547")
        assertEquals("48291547", out)
    }

    @Test
    fun brokenUserRegexFallsBackToDefault() {
        // An invalid regex must not disable extraction outright.
        Prefs.setRegex(ctx, "([")
        val out = OtpExtractor.extract(ctx, "Your verification code is 482915")
        assertEquals("482915", out)
    }

    @Test
    fun cleanupStripsNoisePhrasesBeforeExtraction() {
        Prefs.setCleanupEnabled(ctx, true)
        // A typical cleanup entry: a brand suffix that drags a
        // false-positive digit run into the regex window.
        Prefs.setCleanupPhrases(ctx, listOf("ref-99999"))
        val out = OtpExtractor.extract(
            ctx,
            "ref-99999 Your verification code is 482915",
        )
        assertEquals("482915", out)
    }

    @Test
    fun hasOtpKeywordReportsTriggerPresence() {
        // English trigger.
        assertEquals(true, OtpExtractor.hasOtpKeyword(ctx, "Your verification code is here"))
        // Russian trigger.
        assertEquals(true, OtpExtractor.hasOtpKeyword(ctx, "Ваш одноразовый код"))
        // Plain prose.
        assertEquals(false, OtpExtractor.hasOtpKeyword(ctx, "Just chatting about the weather"))
    }
}
