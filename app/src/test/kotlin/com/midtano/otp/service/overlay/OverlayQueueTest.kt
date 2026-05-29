// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the FIFO queue that backs the overlay service. The
 * service relies on three behaviours: hard-cap protection, content
 * deduplication, and the `currentOtp` race guard.
 */
class OverlayQueueTest {

    private fun show(otp: String, sender: String? = null): PendingShow =
        PendingShow(otp, sender, "sms", "com.example")

    @Test
    fun newQueueIsEmpty() {
        val q = OverlayQueue()
        assertTrue(q.isEmpty())
        assertEquals(0, q.size())
        assertNull(q.pollFirst())
    }

    @Test
    fun rejectsEmptyOtp() {
        val q = OverlayQueue()
        assertFalse(q.offer(PendingShow("", null, null, null), null))
        assertEquals(0, q.size())
    }

    @Test
    fun rejectsNullEntry() {
        val q = OverlayQueue()
        assertFalse(q.offer(null, null))
        assertEquals(0, q.size())
    }

    @Test
    fun acceptsFreshOtp() {
        val q = OverlayQueue()
        assertTrue(q.offer(show("111111"), null))
        assertEquals(1, q.size())
    }

    @Test
    fun rejectsCurrentOtp() {
        // The OTP that's currently on screen must not re-enter
        // through a backlog path.
        val q = OverlayQueue()
        assertFalse(q.offer(show("222222"), currentOtp = "222222"))
        assertTrue(q.isEmpty())
    }

    @Test
    fun rejectsDuplicateAlreadyQueued() {
        val q = OverlayQueue()
        assertTrue(q.offer(show("333333"), null))
        assertFalse(q.offer(show("333333"), null))
        assertEquals(1, q.size())
    }

    @Test
    fun pollFirstReturnsHeadInOrder() {
        val q = OverlayQueue()
        q.offer(show("AAA111"), null)
        q.offer(show("BBB222"), null)
        q.offer(show("CCC333"), null)
        assertEquals("AAA111", q.pollFirst()?.otp)
        assertEquals("BBB222", q.pollFirst()?.otp)
        assertEquals("CCC333", q.pollFirst()?.otp)
        assertNull(q.pollFirst())
    }

    @Test
    fun containsLooksAtAllEntries() {
        val q = OverlayQueue()
        q.offer(show("111111"), null)
        q.offer(show("222222"), null)
        assertTrue(q.contains("111111"))
        assertTrue(q.contains("222222"))
        assertFalse(q.contains("999999"))
        assertFalse(q.contains(null))
    }

    @Test
    fun removeDropsSpecificEntry() {
        val q = OverlayQueue()
        val a = show("AAA")
        val b = show("BBB")
        q.offer(a, null)
        q.offer(b, null)
        assertTrue(q.remove(a))
        assertEquals(1, q.size())
        assertFalse(q.contains("AAA"))
        assertNotNull(q.pollFirst()?.also { assertEquals("BBB", it.otp) })
    }

    @Test
    fun clearWipesEverything() {
        val q = OverlayQueue()
        q.offer(show("AAA"), null)
        q.offer(show("BBB"), null)
        q.clear()
        assertTrue(q.isEmpty())
        assertNull(q.pollFirst())
    }

    @Test
    fun iteratesInInsertionOrder() {
        val q = OverlayQueue()
        q.offer(show("AAA"), null)
        q.offer(show("BBB"), null)
        q.offer(show("CCC"), null)
        val seen = q.map { it.otp }
        assertEquals(listOf("AAA", "BBB", "CCC"), seen)
    }

    @Test
    fun rejectsBeyondHardCap() {
        // Soft regression: a rogue notification stream must not be
        // able to OOM the service. Push HARD_CAP unique entries —
        // every one accepted — then try once more — rejected.
        val q = OverlayQueue()
        for (i in 0 until OverlayQueue.HARD_CAP) {
            assertTrue("entry $i must be accepted", q.offer(show("c%05d".format(i)), null))
        }
        assertEquals(OverlayQueue.HARD_CAP, q.size())
        assertFalse(q.offer(show("overflow"), null))
        assertEquals(OverlayQueue.HARD_CAP, q.size())
    }
}
