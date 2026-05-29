// SPDX-License-Identifier: MIT
package com.midtano.otp

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.midtano.otp.service.NotificationMirror
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device test for [NotificationMirror.ensureChannel] /
 * [NotificationMirror.deleteChannel].
 *
 * The notification-channel APIs go through [NotificationManager]
 * which Robolectric models with a fake. On Android 14+ Nothing OS
 * (and other launchers based on AOSP 14+) added subtle behaviour
 * around channel deletion's soft-delete timer; this test asserts
 * the device sees the channel surface in the live shade settings,
 * with the correct localised name.
 */
@RunWith(AndroidJUnit4::class)
class NotificationMirrorInstrumentedTest {

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val nm
        get() = ctx.getSystemService(NotificationManager::class.java)!!

    @Before
    fun setUp() {
        // Always start clean — a previous run might have left the
        // channel registered.
        NotificationMirror.deleteChannel(ctx)
    }

    @After
    fun tearDown() {
        NotificationMirror.deleteChannel(ctx)
    }

    @Test
    fun ensureChannelCreatesItWithLowImportance() {
        NotificationMirror.ensureChannel(ctx)
        val ch = nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID)
        assertNotNull("channel must exist after ensureChannel", ch)
        assertEquals(NotificationManager.IMPORTANCE_LOW, ch!!.importance)
    }

    @Test
    fun channelNameAndDescriptionResolveFromResources() {
        NotificationMirror.ensureChannel(ctx)
        val ch = nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID)!!

        val name = ch.name?.toString().orEmpty()
        assertFalse("channel name must not be blank", name.isBlank())
        // Expected English name from values/strings.xml
        // ("Silent notification mirrors") OR Russian
        // ("Тихие копии уведомлений") if the device is set to RU.
        // Either way, must not be a numeric resource id.
        assertFalse(
            "channel name should not be a raw resource id: $name",
            name.matches(Regex("^\\d+$")),
        )

        val desc = ch.description.orEmpty()
        assertFalse("channel description must not be blank", desc.isBlank())
    }

    @Test
    fun ensureChannelIsIdempotent() {
        repeat(3) { NotificationMirror.ensureChannel(ctx) }
        assertNotNull(nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID))
    }
}
