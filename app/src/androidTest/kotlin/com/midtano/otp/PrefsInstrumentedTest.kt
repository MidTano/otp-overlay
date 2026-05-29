// SPDX-License-Identifier: MIT
package com.midtano.otp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midtano.otp.data.Prefs
import com.midtano.otp.data.prefs.DisplayMode
import com.midtano.otp.data.prefs.FxLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented round-trip tests for [Prefs] running against the
 * real SharedPreferences on the connected device.
 *
 * Robolectric covers the same code paths in unit tests, but the
 * device-side run also exercises the OEM's KeyValueStore
 * implementation (Nothing OS modifies SharedPrefs persistence on
 * Android 14+). A regression where the storage shape silently
 * changes on a particular OEM build would slip past Robolectric
 * but be caught here.
 */
@RunWith(AndroidJUnit4::class)
class PrefsInstrumentedTest {

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

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
    fun phraseListsRoundTrip() {
        val triggers = listOf("code", "otp", "verify")
        Prefs.setTriggerWords(ctx, triggers)
        assertEquals(triggers, Prefs.getTriggerWords(ctx))

        val stops = listOf("payment", "refund")
        Prefs.setStopWords(ctx, stops)
        assertEquals(stops, Prefs.getStopWords(ctx))
    }

    @Test
    fun shadeDurationClampsToBounds() {
        Prefs.setShadeDurationMs(ctx, 0)
        assertEquals(Prefs.SHADE_DURATION_MIN_MS, Prefs.getShadeDurationMs(ctx))

        Prefs.setShadeDurationMs(ctx, Int.MAX_VALUE)
        assertEquals(Prefs.SHADE_DURATION_MAX_MS, Prefs.getShadeDurationMs(ctx))
    }

    @Test
    fun resetAllExceptOnboardingPreservesOnboardingFlag() {
        Prefs.setOnboardingDone(ctx, true)
        Prefs.setBackCopy(ctx, true)

        Prefs.resetAllExceptOnboarding(ctx)

        assertTrue(Prefs.isOnboardingDone(ctx))
        // After reset other prefs return to declared defaults.
        assertFalse(Prefs.isBackCopy(ctx))
    }
}
