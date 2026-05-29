// SPDX-License-Identifier: MIT
package com.midtano.otp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midtano.otp.data.Prefs
import com.midtano.otp.extractor.OtpDeduplicator
import com.midtano.otp.extractor.OtpExtractor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented end-to-end test of the Context-aware OTP extractor.
 *
 * Same surface as `OtpExtractorRobolectricTest` in unit tests, but
 * resolved through the device's real string resources, real
 * SharedPreferences and real regex engine. Confirms that the OEM
 * regex implementation (e.g. Nothing OS's tweaked ICU build)
 * behaves the same as the test-time version.
 */
@RunWith(AndroidJUnit4::class)
class OtpExtractorInstrumentedTest {

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        Prefs.resetAllExceptOnboarding(ctx)
        OtpDeduplicator.clearForTest()
    }

    @After
    fun tearDown() {
        Prefs.resetAllExceptOnboarding(ctx)
        OtpDeduplicator.clearForTest()
    }

    @Test
    fun extractsTypicalEnglishOtp() {
        assertEquals("482915", OtpExtractor.extract(ctx, "Your verification code is 482915"))
    }

    @Test
    fun extractsTypicalRussianOtp() {
        assertEquals("384712", OtpExtractor.extract(ctx, "Ваш одноразовый код: 384712"))
    }

    @Test
    fun rejectsBodyWithoutTrigger() {
        assertNull(OtpExtractor.extract(ctx, "Bus 482915 leaves at 15:30 from terminal C"))
    }

    @Test
    fun honoursCustomRegex() {
        Prefs.setRegex(ctx, "(\\d{8})")
        assertNull(OtpExtractor.extract(ctx, "Your code is 482915"))
        assertEquals("48291547", OtpExtractor.extract(ctx, "Your code is 48291547"))
    }

    @Test
    fun ignoreListBlocksExtraction() {
        Prefs.setIgnoreEnabled(ctx, true)
        Prefs.setIgnoredPhrases(ctx, listOf("promotional"))
        assertNull(
            OtpExtractor.extract(
                ctx,
                "promotional offer: your verification code is 482915",
            ),
        )
    }

    @Test
    fun normalizeDigitsFoldsArabicIndic() {
        Prefs.setNormalizeDigits(ctx, true)
        // Eastern-Arabic 482915 → ASCII before the regex pass.
        assertEquals(
            "482915",
            OtpExtractor.extract(
                ctx,
                "Your verification code is \u0664\u0668\u0662\u0669\u0661\u0665",
            ),
        )
    }

    @Test
    fun stopWordSuppressesExtraction() {
        Prefs.setStopWordsEnabled(ctx, true)
        Prefs.setStopWords(ctx, listOf("payment"))
        assertNull(
            OtpExtractor.extract(
                ctx,
                "Your verification code is 482915 for the payment",
            ),
        )
    }
}
