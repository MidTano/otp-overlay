// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric tests for [OverlayClipboard].
 *
 * Verifies the OTP lands on the primary clip with the
 * sensitive-content flag set on API 33+, and that the
 * `clearPrimary` path drops it. Privacy-critical: missing the
 * sensitive flag would leak the OTP into the keyboard's
 * recently-copied banner.
 */
@RunWith(AndroidJUnit4::class)
class OverlayClipboardTest {

    private val ctx
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val cm
        get() = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Before
    fun setUp() {
        cm.clearPrimaryClip()
    }

    @After
    fun tearDown() {
        cm.clearPrimaryClip()
    }

    @Test
    fun copyPlacesOtpOnPrimaryClipWithLabel() {
        OverlayClipboard.copy(ctx, "482915")

        val clip = cm.primaryClip
        assertNotNull("clipboard must hold the OTP", clip)
        assertEquals(1, clip!!.itemCount)
        assertEquals("482915", clip.getItemAt(0).text.toString())
        assertEquals(OverlayClipboard.LABEL, clip.description.label)
    }

    @Test
    fun copyMarksSensitiveOn33Plus() {
        // Robolectric defaults to the manifest's targetSdk (36), so
        // the SDK_INT path that stamps the EXTRA_IS_SENSITIVE flag
        // is exercised here.
        OverlayClipboard.copy(ctx, "482915")

        val extras = cm.primaryClip!!.description.extras
        assertNotNull("extras bundle must be set on Android 13+", extras)
        assertTrue(
            "EXTRA_IS_SENSITIVE must be true",
            extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false),
        )
    }

    @Test
    fun copyIgnoresEmptyAndNullInputs() {
        OverlayClipboard.copy(ctx, null)
        assertNull(cm.primaryClip)

        OverlayClipboard.copy(ctx, "")
        assertNull(cm.primaryClip)
    }

    @Test
    fun copyIgnoresNullContext() {
        // Must not throw; clipboard remains untouched.
        OverlayClipboard.copy(null, "482915")
        assertNull(cm.primaryClip)
    }

    @Test
    fun clearPrimaryDropsExistingClip() {
        OverlayClipboard.copy(ctx, "482915")
        assertNotNull(cm.primaryClip)

        OverlayClipboard.clearPrimary(ctx)
        // Robolectric mirrors the platform: clearPrimary nulls out
        // the primary clip on supported OS versions.
        assertNull(cm.primaryClip)
    }

    @Test
    fun clearPrimaryOnEmptyClipboardIsNoOp() {
        OverlayClipboard.clearPrimary(ctx)
        assertNull(cm.primaryClip)
    }

    @Test
    fun clearPrimaryWithNullContextIsNoOp() {
        // Must not throw.
        OverlayClipboard.clearPrimary(null)
    }

    @Test
    fun markSensitiveStampsExtrasBundle() {
        val clip = android.content.ClipData.newPlainText("test", "abc")
        OverlayClipboard.markSensitive(clip)
        val extras = clip.description.extras
        assertNotNull(extras)
        assertTrue(extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false))
    }
}
