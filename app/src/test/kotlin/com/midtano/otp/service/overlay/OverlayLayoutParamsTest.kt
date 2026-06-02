// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric tests for [OverlayLayoutParams].
 *
 * The card window must be locked to the device's portrait
 * short-edge so it never stretches awkwardly across landscape.
 * The toast pill must wrap content with an 8 dp top inset.
 */
@RunWith(AndroidJUnit4::class)
class OverlayLayoutParamsTest {

    private val ctx
        get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun cardParamsUseCorrectWindowType() {
        val lp = OverlayLayoutParams.buildCardParams(ctx)
        val expected = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        assertEquals(expected, lp.type)
    }

    @Test
    fun toastParamsUseCorrectWindowType() {
        val lp = OverlayLayoutParams.buildToastParams(ctx)
        val expected = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        assertEquals(expected, lp.type)
    }

    @Test
    fun cardParamsHaveExpectedFlags() {
        val lp = OverlayLayoutParams.buildCardParams(ctx)
        // Must NOT be focusable (the app keeps IME focus) but must
        // be hit-testable, hardware-accelerated, and lay out under
        // the system bars.
        assertTrue(
            "FLAG_NOT_TOUCH_MODAL must be set",
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0,
        )
        assertTrue(
            "FLAG_LAYOUT_IN_SCREEN must be set",
            lp.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0,
        )
        assertTrue(
            "FLAG_ALT_FOCUSABLE_IM must be set",
            lp.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0,
        )
        assertTrue(
            "FLAG_HARDWARE_ACCELERATED must be set",
            lp.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0,
        )
    }

    @Test
    fun cardParamsUseTopCentreGravity() {
        val lp = OverlayLayoutParams.buildCardParams(ctx)
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, lp.gravity)
    }

    @Test
    fun cardParamsUsePortraitShortEdgeWidth() {
        val lp = OverlayLayoutParams.buildCardParams(ctx)
        val expected = OverlayLayoutParams.portraitCardWidthPx(ctx)
        assertEquals(expected, lp.width)
        assertNotEquals("width must not be MATCH_PARENT", WindowManager.LayoutParams.MATCH_PARENT, lp.width)
    }

    @Test
    fun cardParamsUseWrapContentHeight() {
        val lp = OverlayLayoutParams.buildCardParams(ctx)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, lp.height)
    }

    @Test
    fun toastParamsAreNonFocusableSoStatusBarStaysReachable() {
        val lp = OverlayLayoutParams.buildToastParams(ctx)
        assertTrue(
            "FLAG_NOT_FOCUSABLE must be set",
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0,
        )
    }

    @Test
    fun toastParamsWrapContent() {
        val lp = OverlayLayoutParams.buildToastParams(ctx)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, lp.width)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, lp.height)
    }

    @Test
    fun toastParamsCarryEightDpTopInset() {
        val lp = OverlayLayoutParams.buildToastParams(ctx)
        val density = ctx.resources.displayMetrics.density
        // Allow ±1 px for sub-pixel rounding across Robolectric profiles.
        val expected = (8f * density).toInt()
        assertTrue(
            "expected ~$expected for top inset, got ${lp.y}",
            kotlin.math.abs(lp.y - expected) <= 1,
        )
    }

    @Test
    fun portraitCardWidthIsTheShortestEdge() {
        val dm = ctx.resources.displayMetrics
        val w = OverlayLayoutParams.portraitCardWidthPx(ctx)
        assertEquals(minOf(dm.widthPixels, dm.heightPixels), w)
    }
}
