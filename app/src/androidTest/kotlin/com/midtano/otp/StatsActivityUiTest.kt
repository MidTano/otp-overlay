// SPDX-License-Identifier: MIT
package com.midtano.otp

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midtano.otp.extractor.OtpStats
import com.midtano.otp.system.IoScope
import com.midtano.otp.ui.stats.StatsActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that [StatsActivity] inflates without crashing on a
 * real device when:
 *  - the stats store is empty,
 *  - the store has a few events.
 *
 * Validates the bar-chart drawing path under both empty and
 * populated states. We don't assert pixel-level chart contents —
 * that's brittle across device dpi profiles. A successful inflate
 * + first-frame layout is enough to surface NullPointerException
 * / DivisionByZero / IndexOutOfBoundsException regressions in the
 * StatsActivity binders.
 */
@RunWith(AndroidJUnit4::class)
class StatsActivityUiTest {

    @get:Rule
    val rule: ActivityScenarioRule<StatsActivity> =
        ActivityScenarioRule(StatsActivity::class.java)

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        OtpStats.clear(ctx)
    }

    @After
    fun tearDown() {
        OtpStats.clear(ctx)
    }

    @Test
    fun statsActivityLaunchesWithEmptyStore() {
        rule.scenario.onActivity { activity ->
            // Inflated successfully. The binder must guard against
            // an empty event list (zero senders, all-zero day
            // buckets) without throwing.
            assert(!activity.isFinishing) { "activity finished mid-inflate" }
        }
    }

    @Test
    fun statsActivityLaunchesWithPopulatedStore() {
        runBlocking {
            OtpStats.record(ctx, "Bank A", "sms", "com.bank.a")
            OtpStats.record(ctx, "Bank B", "push", "com.bank.b")
            OtpStats.record(ctx, "Bank A", "sms", "com.bank.a")
            // Drain IoScope before re-launching so the recorded
            // events are visible on activity inflate.
            IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        }

        rule.scenario.recreate()
        rule.scenario.onActivity { activity ->
            assert(!activity.isFinishing) { "activity finished mid-inflate" }
        }
    }
}
