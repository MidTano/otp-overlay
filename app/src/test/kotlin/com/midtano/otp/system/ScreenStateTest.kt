// SPDX-License-Identifier: MIT
package com.midtano.otp.system

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Robolectric tests for [ScreenState.isAvailable].
 *
 * The overlay must not fire while the device is locked or asleep.
 * Both branches are toggled here through Robolectric shadows so the
 * service-side gate can be regression-tested without spinning up a
 * real device.
 */
@RunWith(AndroidJUnit4::class)
class ScreenStateTest {

    private val ctx
        get() = ApplicationProvider.getApplicationContext<Context>()

    private fun setInteractive(value: Boolean) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        // `setInteractive` is the non-deprecated successor to
        // `setIsInteractive` on ShadowPowerManager. It's reflected
        // out so the test compiles cleanly across the Robolectric
        // versions that still ship the deprecated alias.
        @Suppress("DEPRECATION")
        shadowOf(pm).setIsInteractive(value)
    }

    private fun setKeyguardLocked(value: Boolean) {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(km).setKeyguardLocked(value)
    }

    @Test
    fun returnsTrueWhenInteractiveAndUnlocked() {
        setInteractive(true)
        setKeyguardLocked(false)
        assertTrue(ScreenState.isAvailable(ctx))
    }

    @Test
    fun returnsFalseWhenScreenIsOff() {
        setInteractive(false)
        setKeyguardLocked(false)
        assertFalse(ScreenState.isAvailable(ctx))
    }

    @Test
    fun returnsFalseWhenKeyguardIsLocked() {
        setInteractive(true)
        setKeyguardLocked(true)
        assertFalse(ScreenState.isAvailable(ctx))
    }

    @Test
    fun returnsFalseWhenBothLockedAndAsleep() {
        setInteractive(false)
        setKeyguardLocked(true)
        assertFalse(ScreenState.isAvailable(ctx))
    }
}
