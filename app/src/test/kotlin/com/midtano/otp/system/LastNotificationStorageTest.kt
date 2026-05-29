// SPDX-License-Identifier: MIT
package com.midtano.otp.system

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric integration tests for [LastNotification].
 *
 * Exercises the full save → persist → read pipeline against a
 * Robolectric SharedPreferences-backed `Context`, including the
 * privacy guarantee that no OTP-shaped digit run reaches disk.
 */
@RunWith(AndroidJUnit4::class)
class LastNotificationStorageTest {

    private val ctx
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        LastNotification.clear(ctx)
    }

    @After
    fun tearDown() {
        LastNotification.clear(ctx)
    }

    @Test
    fun emptyStoreReturnsLocalisedPlaceholder() {
        val out = LastNotification.read(ctx)
        // The default resource is "—" in EN; we just want a
        // non-blank, non-stack-tracey placeholder. Robolectric's
        // ApplicationProvider returns a real Context with the EN
        // strings.xml resolved.
        assertFalse("placeholder should not contain 'Read error'", out.contains("Read error"))
        assertTrue("placeholder should be non-empty", out.isNotBlank())
    }

    @Test
    fun saveThenReadRoundtripsWithoutLeakingDigits() = runBlocking {
        LastNotification.save(ctx, "com.example.bank", "Your code is 482915", "extracted 482915")
        // The save runs on IoScope; pump the dispatcher.
        IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }

        val out = LastNotification.read(ctx)
        assertTrue("expected entry header in store: $out", out.contains("com.example.bank"))
        assertFalse("OTP digits leaked into store: $out", out.contains("482915"))
        assertTrue("expected redaction marker: $out", out.contains("***6 digits"))
    }

    @Test
    fun phoneLikeNumbersAreMaskedInPersistedBody() = runBlocking {
        LastNotification.save(
            ctx,
            "com.example.bank",
            "From +71234567890 your code is 482915",
            "extracted 482915",
        )
        IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }

        val out = LastNotification.read(ctx)
        assertFalse("phone digits leaked: $out", out.contains("71234567890"))
        assertFalse("OTP digits leaked: $out", out.contains("482915"))
        assertTrue("expected phone marker: $out", out.contains("***11-digit-phone"))
        assertTrue("expected OTP marker: $out", out.contains("***6 digits"))
    }

    @Test
    fun ringBufferCapsAtConfiguredMax() = runBlocking {
        // Save more than MAX_ENTRIES (30) records and verify the
        // store caps at exactly MAX_ENTRIES afterwards. Order of
        // arrival depends on `IoScope.launch` scheduling which is
        // not deterministic — what we DO guarantee is the cap, not
        // which exact entries fell off.
        for (i in 0 until 35) {
            LastNotification.save(ctx, "pkg.$i", "body of message $i", "verdict $i")
        }
        IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }

        val out = LastNotification.read(ctx)
        // Exactly 30 [#N] markers (1..30) must be present.
        val markers = "\\[#\\d+]".toRegex().findAll(out).count()
        assertEquals("ring should hold exactly MAX_ENTRIES rows", 30, markers)
        // 5 entries were evicted; we don't pin which ones, but the
        // total set of survivors must come from pkg.0..pkg.34.
        val pkgRefs = "pkg\\.(\\d+)".toRegex().findAll(out)
            .map { it.groupValues[1].toInt() }
            .toSet()
        assertTrue("expected ~30 distinct pkg references, got $pkgRefs", pkgRefs.size >= 25)
        assertTrue("survivor set must subset 0..34", pkgRefs.all { it in 0..34 })
    }

    @Test
    fun longBodiesGetTruncated() = runBlocking {
        val veryLong = "x".repeat(2_000) + " code 482915 end"
        LastNotification.save(ctx, "com.example", veryLong, "extracted")
        IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }

        val out = LastNotification.read(ctx)
        assertTrue("expected truncation marker: $out", out.contains("[truncated"))
        assertFalse("OTP must still be redacted post-truncation: $out", out.contains("482915"))
    }

    @Test
    fun clearWipesEverything() = runBlocking {
        LastNotification.save(ctx, "pkg.A", "code 111111", "verdict A")
        IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        // Sanity — body present.
        assertTrue(LastNotification.read(ctx).contains("pkg.A"))

        LastNotification.clear(ctx)
        val afterClear = LastNotification.read(ctx)
        assertFalse(afterClear.contains("pkg.A"))
        assertFalse(afterClear.contains("verdict A"))
    }

    @Test
    fun nullPackageDoesNotCrashTheStore() = runBlocking {
        LastNotification.save(ctx, null, "code 111111", "extracted")
        IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        val out = LastNotification.read(ctx)
        assertTrue("expected '?' placeholder for null pkg: $out", out.contains("?"))
        assertFalse("OTP must still be redacted: $out", out.contains("111111"))
    }

    @Test
    fun verdictIsRedactedTooNotJustBody() = runBlocking {
        LastNotification.save(
            ctx,
            "com.example",
            "no code here",
            "extracted 482915 from body",
        )
        IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
        val out = LastNotification.read(ctx)
        assertFalse("OTP leaked from verdict: $out", out.contains("482915"))
        assertTrue("verdict redactor should mark the OTP run: $out", out.contains("***6 digits"))
    }

    @Test
    fun multipleSavesAreSerialisedAndAllPersist() = runBlocking {
        // Fire several saves in rapid succession; the IoScope
        // serialises through a synchronized(this) block so no save
        // can lose its entry to a read-modify-write race.
        repeat(10) { i ->
            LastNotification.save(ctx, "pkg.$i", "msg $i with code 11111$i", "verdict $i")
        }
        IoScope.scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }

        val out = LastNotification.read(ctx)
        for (i in 0 until 10) {
            assertTrue("missing pkg.$i in: $out", out.contains("pkg.$i"))
            assertFalse("leaked code 11111$i in: $out", out.contains("11111$i"))
        }
        // Exactly 10 entries indexed [#1]..[#10].
        assertEquals(10, "\\[#".toRegex().findAll(out).count())
    }
}
