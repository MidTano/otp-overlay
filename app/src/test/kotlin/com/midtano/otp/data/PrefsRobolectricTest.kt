// SPDX-License-Identifier: MIT
package com.midtano.otp.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midtano.otp.data.prefs.DisplayMode
import com.midtano.otp.data.prefs.FxLevel
import com.midtano.otp.locale.AppLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric integration tests for the [Prefs] facade.
 *
 * Exercises the full SharedPreferences round-trip so a future
 * refactor that quietly changes a key, default value or storage
 * shape (typed enum vs int, list vs comma-string) is caught at
 * test time rather than by a user complaining about lost
 * preferences after an upgrade.
 */
@RunWith(AndroidJUnit4::class)
class PrefsRobolectricTest {

    private val ctx
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        Prefs.resetAllExceptOnboarding(ctx)
    }

    @After
    fun tearDown() {
        Prefs.resetAllExceptOnboarding(ctx)
    }

    @Test
    fun coreBooleansRoundTrip() {
        Prefs.setBackCopy(ctx, true)
        Prefs.setAutoPaste(ctx, true)
        Prefs.setSounds(ctx, false)

        assertTrue(Prefs.isBackCopy(ctx))
        assertTrue(Prefs.isAutoPaste(ctx))
        assertFalse(Prefs.isSounds(ctx))
    }

    @Test
    fun resetAllExceptOnboardingPreservesOnboardingFlag() {
        Prefs.setOnboardingDone(ctx, true)
        Prefs.setBackCopy(ctx, true)
        Prefs.setAutoPaste(ctx, true)

        Prefs.resetAllExceptOnboarding(ctx)

        // Onboarding survives the wipe.
        assertTrue(Prefs.isOnboardingDone(ctx))
        // Other prefs return to their declared defaults.
        assertNotEquals(true, Prefs.isAutoPaste(ctx))
    }

    @Test
    fun typedDisplayModeRoundTrips() {
        Prefs.setDisplayMode(ctx, DisplayMode.SHADE)
        assertEquals(DisplayMode.SHADE, Prefs.getDisplayModeTyped(ctx))
        assertTrue(Prefs.isShadeMode(ctx))

        Prefs.setDisplayMode(ctx, DisplayMode.OVERLAY)
        assertEquals(DisplayMode.OVERLAY, Prefs.getDisplayModeTyped(ctx))
        assertFalse(Prefs.isShadeMode(ctx))
    }

    @Test
    fun typedFxLevelRoundTrips() {
        Prefs.setFxLevel(ctx, FxLevel.ULTRA)
        assertEquals(FxLevel.ULTRA, Prefs.getFxLevelTyped(ctx))

        Prefs.setFxLevel(ctx, FxLevel.LITE)
        assertEquals(FxLevel.LITE, Prefs.getFxLevelTyped(ctx))
    }

    @Test
    fun localeRoundTrips() {
        Prefs.setLanguageTyped(ctx, AppLanguage.RU)
        assertEquals(AppLanguage.RU, Prefs.getLanguageTyped(ctx))

        Prefs.setLanguageTyped(ctx, AppLanguage.EN)
        assertEquals(AppLanguage.EN, Prefs.getLanguageTyped(ctx))
    }

    @Test
    fun fxKnobsRoundTripWithMinMaxClamping() {
        Prefs.setFxBreathAmt(ctx, 50)
        assertEquals(50, Prefs.getFxBreathAmt(ctx))

        // Min-clamp on negative.
        Prefs.setFxBreathAmt(ctx, -100)
        assertTrue(
            "negative input must be clamped to a non-negative range",
            Prefs.getFxBreathAmt(ctx) >= 0,
        )

        // Max-clamp on extreme value (anything well past plausible UI).
        Prefs.setFxBreathAmt(ctx, 100_000)
        assertTrue(
            "huge input must be clamped to the configured ceiling",
            Prefs.getFxBreathAmt(ctx) <= 500,
        )
    }

    @Test
    fun phraseListsRoundTrip() {
        val triggers = listOf("code", "otp", "verify")
        Prefs.setTriggerWords(ctx, triggers)
        assertEquals(triggers, Prefs.getTriggerWords(ctx))

        val stops = listOf("payment", "refund")
        Prefs.setStopWords(ctx, stops)
        assertEquals(stops, Prefs.getStopWords(ctx))

        val ignored = listOf("promotional", "spam")
        Prefs.setIgnoredPhrases(ctx, ignored)
        assertEquals(ignored, Prefs.getIgnoredPhrases(ctx))

        val cleanup = listOf("ref-99999", "id-12345")
        Prefs.setCleanupPhrases(ctx, cleanup)
        assertEquals(cleanup, Prefs.getCleanupPhrases(ctx))
    }

    @Test
    fun resetTriggerWordsReturnsCanonicalDefaults() {
        Prefs.setTriggerWords(ctx, listOf("only_one"))
        Prefs.resetTriggerWords(ctx)
        val defaults = Prefs.getTriggerWords(ctx)
        assertTrue("expected non-empty defaults", defaults.isNotEmpty())
        assertTrue("default trigger list must include 'code'", defaults.any { it == "code" })
    }

    @Test
    fun allowedAppsRoundTripAsSet() {
        val apps = setOf("com.example.bank", "com.example.messenger")
        Prefs.setAllowedApps(ctx, apps)
        assertEquals(apps, Prefs.getAllowedApps(ctx))
    }

    @Test
    fun packageAllowedRespectsFilterToggle() {
        Prefs.setFilterApps(ctx, false)
        assertTrue("filter off → every package is allowed", Prefs.isPackageAllowed(ctx, "com.any"))

        Prefs.setFilterApps(ctx, true)
        Prefs.setAllowedApps(ctx, setOf("com.example.bank"))
        assertTrue(Prefs.isPackageAllowed(ctx, "com.example.bank"))
        assertFalse(Prefs.isPackageAllowed(ctx, "com.other"))
    }

    @Test
    fun shadeDurationRespectsBounds() {
        // Tries to set below the floor — must clamp to MIN.
        Prefs.setShadeDurationMs(ctx, 0)
        assertEquals(Prefs.SHADE_DURATION_MIN_MS, Prefs.getShadeDurationMs(ctx))

        // Tries to set above the ceiling — must clamp to MAX.
        Prefs.setShadeDurationMs(ctx, Int.MAX_VALUE)
        assertEquals(Prefs.SHADE_DURATION_MAX_MS, Prefs.getShadeDurationMs(ctx))
    }
}
