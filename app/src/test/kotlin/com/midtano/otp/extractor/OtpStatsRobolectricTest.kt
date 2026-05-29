// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.midtano.otp.system.IoScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric integration tests for [OtpStats].
 *
 * Validates the JSON-on-SharedPreferences storage shape, the ring
 * buffer cap, the totals counter, the top-N comparator and the
 * day-bucketing behaviour. None of this is exercisable from a
 * pure-JVM test because the underlying storage relies on a real
 * Android `Context`.
 */
@RunWith(AndroidJUnit4::class)
class OtpStatsRobolectricTest {

    private val ctx
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        OtpStats.clear(ctx)
    }

    @After
    fun tearDown() {
        OtpStats.clear(ctx)
    }

    /** Drain the IoScope so all `record` writes have landed before assertions. */
    private fun drain() = runBlocking {
        IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
    }

    @Test
    fun emptyStoreReturnsEmptyCollections() {
        assertTrue(OtpStats.readAllEvents(ctx).isEmpty())
        assertTrue(OtpStats.topSenders(ctx, 10).isEmpty())
        assertTrue(OtpStats.senderToPkg(ctx).isEmpty())
    }

    @Test
    fun singleRecordRoundTrips() {
        OtpStats.record(ctx, "BankA", "sms", "com.bank.a")
        drain()

        val events = OtpStats.readAllEvents(ctx)
        assertEquals(1, events.size)
        assertEquals("BankA", events[0].sender)
        assertEquals("sms", events[0].source)
        assertEquals("com.bank.a", events[0].pkg)
        assertTrue(events[0].timestamp > 0L)
    }

    @Test
    fun topSendersOrdersByCountDescending() {
        repeat(3) { OtpStats.record(ctx, "BankA", "sms", "com.bank.a") }
        repeat(5) { OtpStats.record(ctx, "BankB", "sms", "com.bank.b") }
        repeat(1) { OtpStats.record(ctx, "BankC", "sms", "com.bank.c") }
        drain()

        val tops = OtpStats.topSenders(ctx, 10)
        assertEquals(3, tops.size)
        assertEquals("BankB", tops[0].sender)
        assertEquals(5, tops[0].count)
        assertEquals("BankA", tops[1].sender)
        assertEquals("BankC", tops[2].sender)
    }

    @Test
    fun topSendersRespectsLimit() {
        for (i in 0 until 7) {
            repeat(i + 1) { OtpStats.record(ctx, "S$i", "sms", "p$i") }
        }
        drain()

        val tops = OtpStats.topSenders(ctx, 3)
        assertEquals(3, tops.size)
        // S6 has the largest count (7), then S5 (6), then S4 (5).
        assertEquals("S6", tops[0].sender)
        assertEquals("S5", tops[1].sender)
        assertEquals("S4", tops[2].sender)
    }

    @Test
    fun emptySenderIsCountedUnderUnknownPlaceholder() {
        OtpStats.record(ctx, null, "sms", "")
        OtpStats.record(ctx, "", "sms", "")
        drain()

        // Both empty-sender events fold into the `stats_unknown_sender`
        // bucket; one totals entry, count = 2.
        val tops = OtpStats.topSenders(ctx, 5)
        assertEquals(1, tops.size)
        assertEquals(2, tops[0].count)
    }

    @Test
    fun senderToPkgMapsLatestPackagePerSender() {
        OtpStats.record(ctx, "BankA", "sms", "com.bank.a.v1")
        // Force a millisecond gap between the two record() calls
        // so their captured timestamps strictly differ. Without
        // this the two events can share a System.currentTimeMillis()
        // tick on a fast Linux CI runner, and the IoScope launches
        // can land in either order — making the "latest" assertion
        // flaky. The production code already breaks ties by chronology
        // (see OtpStats.senderToPkg).
        Thread.sleep(2)
        OtpStats.record(ctx, "BankA", "push", "com.bank.a.v2")
        drain()

        val map = OtpStats.senderToPkg(ctx)
        // Last writer wins per the documented contract.
        assertEquals("com.bank.a.v2", map["BankA"])
    }

    @Test
    fun dailyCountsTodayCountsAllRecentEvents() {
        repeat(4) { OtpStats.record(ctx, "BankA", "sms", "com.bank.a") }
        drain()

        val daily = OtpStats.dailyCounts(ctx, 7)
        assertEquals(7, daily.size)
        assertEquals(4, daily[0])
        // Yesterday and earlier — empty.
        assertEquals(0, daily[1])
        assertEquals(0, daily[6])
    }

    @Test
    fun dailyCountsHandlesZeroDaysGracefully() {
        OtpStats.record(ctx, "BankA", "sms", "com.bank.a")
        drain()

        val daily = OtpStats.dailyCounts(ctx, 0)
        // Implementation clamps to at least 1 bucket.
        assertEquals(1, daily.size)
        assertEquals(1, daily[0])
    }

    @Test
    fun clearWipesEverything() {
        OtpStats.record(ctx, "BankA", "sms", "com.bank.a")
        OtpStats.record(ctx, "BankB", "push", "com.bank.b")
        drain()
        assertEquals(2, OtpStats.readAllEvents(ctx).size)

        OtpStats.clear(ctx)

        assertTrue(OtpStats.readAllEvents(ctx).isEmpty())
        assertTrue(OtpStats.topSenders(ctx, 10).isEmpty())
    }

    @Test
    fun nullContextIsSafeNoOp() {
        // No exception thrown for any of the reader / writer paths.
        OtpStats.record(null, "BankA", "sms", "com.bank.a")
        assertTrue(OtpStats.readAllEvents(null).isEmpty())
        assertTrue(OtpStats.topSenders(null, 10).isEmpty())
        assertTrue(OtpStats.senderToPkg(null).isEmpty())
        OtpStats.clear(null)
    }

    @Test
    fun corruptedStorageDegradesGracefully() {
        // Manually poison the events JSON. The reader must return
        // an empty list rather than throwing — readers are wrapped
        // in try/catch and writers fall back to a fresh array.
        val sp = ctx.applicationContext.getSharedPreferences("otp_stats", android.content.Context.MODE_PRIVATE)
        sp.edit().putString("events", "not-a-json-array").apply()

        assertTrue(OtpStats.readAllEvents(ctx).isEmpty())
    }
}
