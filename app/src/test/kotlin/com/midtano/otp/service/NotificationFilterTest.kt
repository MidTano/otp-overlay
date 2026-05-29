// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the package-name allow-list gate. Covers the
 * three behaviours real notifications rely on:
 * - exact prefix match,
 * - longer-than-prefix package names that still start with the prefix
 *   (OEM shadowing of system components),
 * - the self-package guard so the listener never re-feeds its own
 *   foreground-service notification back into the queue.
 */
class NotificationFilterTest {

    @Test
    fun systemUiIsRejected() {
        assertEquals("com.android.systemui", NotificationFilter.matchedIgnorePrefix("com.android.systemui"))
        assertTrue(NotificationFilter.shouldIgnore("com.android.systemui"))
    }

    @Test
    fun oemShadowingIsRejected() {
        // MIUI / Pixel sub-features shadow systemui under longer names.
        val matched = NotificationFilter.matchedIgnorePrefix("com.android.systemui.intelligence")
        assertEquals("com.android.systemui", matched)
    }

    @Test
    fun searchBoxIsRejected() {
        assertTrue(NotificationFilter.shouldIgnore("com.google.android.googlequicksearchbox"))
    }

    @Test
    fun realAppPassesThrough() {
        assertNull(NotificationFilter.matchedIgnorePrefix("com.bank.example"))
        assertFalse(NotificationFilter.shouldIgnore("com.bank.example"))
    }

    @Test
    fun nullAndEmptyPackageAreRejectedByOverloads() {
        assertTrue(NotificationFilter.shouldIgnore(null, "com.midtano.otp"))
        assertTrue(NotificationFilter.shouldIgnore("", "com.midtano.otp"))
    }

    @Test
    fun selfPackageIsRejectedByOverload() {
        assertTrue(NotificationFilter.shouldIgnore("com.midtano.otp", "com.midtano.otp"))
        assertTrue(NotificationFilter.shouldIgnore("com.midtano.otp.debug", "com.midtano.otp.debug"))
    }

    @Test
    fun selfOverloadStillRunsPrefixCheck() {
        assertTrue(NotificationFilter.shouldIgnore("android", "com.midtano.otp"))
        assertFalse(NotificationFilter.shouldIgnore("com.bank.example", "com.midtano.otp"))
    }
}
