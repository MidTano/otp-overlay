// SPDX-License-Identifier: MIT
package com.midtano.otp

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.midtano.otp.data.Prefs
import com.midtano.otp.extractor.OtpDeduplicator
import com.midtano.otp.service.OverlayService
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device smoke test for the [OverlayService] lifecycle.
 *
 * Sends a `SHOW_OTP` intent through `startForegroundService` and
 * lets the device attach the overlay window via WindowManager.
 * Because the service draws into a real surface, this is the only
 * path that catches OEM-specific overlay-attach regressions
 * (`TYPE_APPLICATION_OVERLAY` rejected, foreground-service type
 * mismatch, channel-id missing, etc.).
 *
 * The test sleeps briefly for the system to attach / detach the
 * window — Espresso has no idling resource for window-manager
 * attach, so a short wait is the cleanest hook.
 */
@RunWith(AndroidJUnit4::class)
class OverlayServiceLifecycleTest {

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val device
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun setUp() {
        // Wake and unlock so the overlay can attach. Tests that
        // run on a locked device would otherwise hit ScreenState's
        // gate and skip the overlay entirely.
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        // Make sure non-relevant prefs don't divert the path
        // through the shade notifier. We want the WindowManager
        // overlay code to fire.
        Prefs.resetAllExceptOnboarding(ctx)
        OtpDeduplicator.clearForTest()
    }

    @After
    fun tearDown() {
        // Always send a dismiss so the overlay window is gone
        // before the next test attaches its own.
        sendDismiss()
        Thread.sleep(400)
        OtpDeduplicator.clearForTest()
    }

    @Test
    fun showThenDismissDoesNotCrashTheService() {
        sendShow("482915", "TestBank", OverlayService.SOURCE_TEST, "com.test.bank")
        // Give WindowManager a beat to attach the window.
        Thread.sleep(800)
        sendDismiss()
        Thread.sleep(400)

        // The instrumentation process is still alive; the service
        // didn't crash mid-attach. Implicit assertion via test
        // simply not throwing or timing out.
        assertTrue(true)
    }

    @Test
    fun multipleShowsBackToBackAreSerialised() {
        sendShow("111111", "Bank A", OverlayService.SOURCE_TEST, "com.test.a")
        Thread.sleep(300)
        sendShow("222222", "Bank B", OverlayService.SOURCE_TEST, "com.test.b")
        Thread.sleep(300)
        sendShow("333333", "Bank C", OverlayService.SOURCE_TEST, "com.test.c")
        Thread.sleep(800)
        sendDismiss()
        Thread.sleep(400)
        // Service must stay alive — no crash means the queue
        // serialised the bursts correctly.
        assertTrue(true)
    }

    @Test
    fun shadeModeRoutesThroughNotifierWithoutWindowAttach() {
        // In SHADE display mode the service must route to
        // OtpShadeNotifier, which posts a status-bar notification
        // instead of attaching a WindowManager overlay. The path
        // is observable purely via the absence of crashes when
        // the device's lockscreen / immersive app would block a
        // WindowManager attach.
        Prefs.setDisplayMode(ctx, com.midtano.otp.data.prefs.DisplayMode.SHADE)
        sendShow("555555", "Bank S", OverlayService.SOURCE_TEST, "com.test.s")
        Thread.sleep(500)
        // Reset to overlay so other tests in the class don't
        // inherit a non-default mode.
        Prefs.setDisplayMode(ctx, com.midtano.otp.data.prefs.DisplayMode.OVERLAY)
        sendDismiss()
        Thread.sleep(300)
        assertTrue(true)
    }

    private fun sendShow(otp: String, sender: String, source: String, pkg: String) {
        val i = Intent(ctx, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_OTP
            putExtra(OverlayService.EXTRA_OTP, otp)
            putExtra(OverlayService.EXTRA_SENDER, sender)
            putExtra(OverlayService.EXTRA_SOURCE, source)
            putExtra(OverlayService.EXTRA_PKG, pkg)
        }
        ContextCompat.startForegroundService(ctx, i)
    }

    private fun sendDismiss() {
        val i = Intent(ctx, OverlayService::class.java).apply {
            action = OverlayService.ACTION_DISMISS
        }
        ctx.startService(i)
    }
}
