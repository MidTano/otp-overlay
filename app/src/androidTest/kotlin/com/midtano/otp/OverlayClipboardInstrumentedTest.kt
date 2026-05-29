// SPDX-License-Identifier: MIT
package com.midtano.otp

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midtano.otp.service.overlay.OverlayClipboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device test for [OverlayClipboard].
 *
 * Notes about Android 13+ clipboard policy:
 * Starting on API 33, only the foreground app can call
 * `setPrimaryClip` and have it succeed silently — background
 * processes (including instrumentation tests that don't host an
 * Activity in the foreground) get rate-limited or blocked. The
 * production code path runs from `OverlayService` while it owns an
 * attached WindowManager surface, which the platform treats as
 * foreground for clipboard purposes, so the production write
 * succeeds. The test surface here therefore validates:
 *
 *  - The helper does NOT throw on null/empty input (defensive
 *    contract).
 *  - The label constant is the documented `"OTP"` value.
 *  - [OverlayClipboard.markSensitive] stamps the
 *    EXTRA_IS_SENSITIVE flag onto a freshly-built ClipData,
 *    independent of whether the platform later accepts the clip
 *    onto the primary slot — privacy-critical, since this is the
 *    flag that hides the OTP from the keyboard banner.
 */
@RunWith(AndroidJUnit4::class)
class OverlayClipboardInstrumentedTest {

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun copyIgnoresNullAndEmptyInputsWithoutCrashing() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cm.clearPrimaryClip()
            OverlayClipboard.copy(ctx, null)
            OverlayClipboard.copy(ctx, "")
        }
        assertNull("null/empty inputs must not write to the clipboard", cm.primaryClip)
    }

    @Test
    fun copyIgnoresNullContextWithoutCrashing() {
        // Defensive null-context contract — the production code
        // path forwards the live service context, but the helper
        // must not throw if it's ever called with null.
        OverlayClipboard.copy(null, "482915")
    }

    @Test
    fun clearPrimaryWithNullContextIsNoOp() {
        OverlayClipboard.clearPrimary(null)
    }

    @Test
    fun markSensitiveStampsExtrasBundle() {
        // Build a ClipData manually and run the helper directly —
        // independent of whether the platform accepts the clip
        // onto the primary slot. This is the real privacy-critical
        // path: any future regression that drops the
        // EXTRA_IS_SENSITIVE flag would let the keyboard banner
        // echo the OTP.
        val clip = android.content.ClipData.newPlainText("test", "abc")
        OverlayClipboard.markSensitive(clip)
        val extras = clip.description.extras
        assertNotNull("extras bundle must be set on Android 13+", extras)
        assertTrue(
            "EXTRA_IS_SENSITIVE must be true after markSensitive",
            extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false),
        )
    }

    @Test
    fun labelIsDocumentedConstant() {
        assertEquals("OTP", OverlayClipboard.LABEL)
    }
}
