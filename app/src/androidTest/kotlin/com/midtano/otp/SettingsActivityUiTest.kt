// SPDX-License-Identifier: MIT
package com.midtano.otp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midtano.otp.data.Prefs
import com.midtano.otp.ui.settings.SettingsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso smoke test for [SettingsActivity].
 *
 * Verifies the activity launches without crashing on a real
 * device, that its key controls are visible, and that toggling
 * the `back_copy` switch persists into [Prefs] (a simple binder
 * regression the unit tests can't observe end-to-end).
 *
 * Activity launch covers a lot of ground — every binder runs in
 * `onCreate`, all locale-tagged TextViews resolve, every drawable
 * inflates. A crash here surfaces as an instrumentation error.
 *
 * The settings layout is a long vertical scroll, so every
 * `onView` interaction routes through `scrollTo()` first to
 * survive small CI-emulator viewports where individual switches
 * sit off-screen on activity start.
 */
@RunWith(AndroidJUnit4::class)
class SettingsActivityUiTest {

    @get:Rule
    val rule: ActivityScenarioRule<SettingsActivity> =
        ActivityScenarioRule(SettingsActivity::class.java)

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
    fun activityLaunchesWithCoreSettingsVisible() {
        // scrollTo() makes the assertion robust on small CI
        // emulator viewports where the target control may not
        // be visible on activity start. After the scroll, the
        // switch is guaranteed to be on screen.
        onView(withId(R.id.switch_back_action))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withId(R.id.switch_auto_paste))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun togglingBackCopySwitchPersists() {
        val before = Prefs.isBackCopy(ctx)
        onView(withId(R.id.switch_back_action))
            .perform(scrollTo(), click())
        // The toggle is animated; give Espresso a moment for the
        // listener callback to mutate prefs.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(!before, Prefs.isBackCopy(ctx))
    }

    @Test
    fun togglingAutoPasteSwitchPersists() {
        val before = Prefs.isAutoPaste(ctx)
        onView(withId(R.id.switch_auto_paste))
            .perform(scrollTo(), click())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(!before, Prefs.isAutoPaste(ctx))
    }
}
