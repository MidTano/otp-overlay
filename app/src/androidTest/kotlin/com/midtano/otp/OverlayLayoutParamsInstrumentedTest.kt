// SPDX-License-Identifier: MIT
package com.midtano.otp

import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midtano.otp.service.overlay.OverlayLayoutParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device test for [OverlayLayoutParams]. The Robolectric
 * counterpart asserts the same flags but Robolectric's display
 * metrics are synthetic — this run validates that on a real
 * Nothing OS device, the portrait short-edge picker sees the
 * correct hardware width.
 */
@RunWith(AndroidJUnit4::class)
class OverlayLayoutParamsInstrumentedTest {

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cardParamsCarryExpectedFlagsAndType() {
        val lp = OverlayLayoutParams.buildCardParams(ctx)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, lp.type)
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, lp.gravity)
        assertTrue(
            "FLAG_NOT_TOUCH_MODAL must be set",
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0,
        )
        assertTrue(
            "FLAG_HARDWARE_ACCELERATED must be set",
            lp.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0,
        )
        assertTrue(
            "FLAG_ALT_FOCUSABLE_IM must be set",
            lp.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0,
        )
    }

    @Test
    fun portraitWidthIsTheRealDeviceShortEdge() {
        val dm = ctx.resources.displayMetrics
        val expected = minOf(dm.widthPixels, dm.heightPixels)
        assertEquals(expected, OverlayLayoutParams.portraitCardWidthPx(ctx))
        assertTrue(
            "portrait width must be > 0 on a real screen",
            expected > 0,
        )
    }

    @Test
    fun toastParamsAreNonFocusableAndWrapContent() {
        val lp = OverlayLayoutParams.buildToastParams(ctx)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, lp.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, lp.height)
        assertTrue(
            "FLAG_NOT_FOCUSABLE must be set",
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0,
        )
    }

    @Test
    fun toastParamsHaveEightDpTopInset() {
        val lp = OverlayLayoutParams.buildToastParams(ctx)
        val density = ctx.resources.displayMetrics.density
        val expected = (8f * density).toInt()
        // Allow ±1px for rounding across density profiles.
        assertTrue(
            "expected ~$expected for top inset, got ${lp.y}",
            kotlin.math.abs(lp.y - expected) <= 1,
        )
    }
}
