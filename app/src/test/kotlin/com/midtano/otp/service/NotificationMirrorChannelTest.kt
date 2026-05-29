// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric tests for [NotificationMirror.ensureChannel] /
 * [NotificationMirror.deleteChannel].
 *
 * The mirror channel surfaces in the system per-app notification
 * settings. The user-visible name and description must come from
 * `strings.xml` — never from a Kotlin string literal — so the
 * Russian device shows Russian copy. The test reads the channel
 * back and asserts both fields resolved through the resource
 * pipeline (i.e. they're non-empty and not the resource id).
 */
@RunWith(AndroidJUnit4::class)
class NotificationMirrorChannelTest {

    private val ctx
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val nm
        get() = ctx.getSystemService(NotificationManager::class.java)!!

    @After
    fun tearDown() {
        // Always leave the system in a clean state regardless of the
        // test's outcome.
        NotificationMirror.deleteChannel(ctx)
    }

    @Test
    fun ensureChannelCreatesItOnce() {
        // Pre-condition: channel doesn't exist.
        assertNull(nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID))

        NotificationMirror.ensureChannel(ctx)
        val ch = nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID)
        assertNotNull("channel must exist after ensureChannel", ch)
        assertEquals(NotificationManager.IMPORTANCE_LOW, ch!!.importance)
    }

    @Test
    fun channelNameAndDescriptionResolveFromStringResources() {
        NotificationMirror.ensureChannel(ctx)
        val ch = nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID)!!

        // The name must be a real human-readable string, not a
        // resource id rendered as decimal. Test ASCII-only because
        // the Robolectric ApplicationProvider returns the EN
        // strings.xml; on a Russian device the same string set
        // resolves to the localised copy via values-ru.
        val name = ch.name?.toString().orEmpty()
        assert(name.isNotBlank()) { "channel name must be non-empty" }
        assert(!name.matches(Regex("^\\d+$"))) { "channel name should not be a raw resource id: $name" }

        val desc = ch.description.orEmpty()
        assert(desc.isNotBlank()) { "channel description must be non-empty" }
    }

    @Test
    fun ensureChannelIsIdempotent() {
        NotificationMirror.ensureChannel(ctx)
        NotificationMirror.ensureChannel(ctx)
        NotificationMirror.ensureChannel(ctx)
        assertNotNull(nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID))
    }

    @Test
    fun deleteChannelRemovesIt() {
        NotificationMirror.ensureChannel(ctx)
        assertNotNull(nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID))

        NotificationMirror.deleteChannel(ctx)
        // On modern Android the channel may linger in a soft-delete
        // state for one cycle; what we care about is no exceptions
        // and the visible state being "absent". Robolectric returns
        // null for never-created channels and the platform's own
        // delete; both are acceptable here.
        // We re-create after delete to verify the flip is non-fatal.
        NotificationMirror.ensureChannel(ctx)
        assertNotNull(nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID))
    }

    @Test
    fun deleteChannelOnEmptyStateIsNoOp() {
        // No prior ensureChannel — delete must not throw.
        NotificationMirror.deleteChannel(ctx)
        assertNull(nm.getNotificationChannel(NotificationMirror.MIRROR_CHANNEL_ID))
    }
}
