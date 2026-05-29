// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Callable
import java.util.regex.Pattern

/**
 * Tests for the ReDoS protection layer. Verifies three contracts:
 *
 *  1. Healthy work returns its result when it finishes inside the
 *     wall-clock budget.
 *  2. A pathological pattern that would otherwise hang the matcher
 *     gets aborted via [InterruptibleCharSequence] and returns
 *     `null`.
 *  3. Throwing work returns `null` rather than propagating the
 *     exception out to the listener thread.
 */
class RegexTimeoutTest {

    @Test
    fun fastWorkReturnsResult() {
        val out = RegexTimeout.run(Callable { 42 }, 250L)
        assertEquals(42, out)
    }

    @Test
    fun nullSafeWhenWorkReturnsNull() {
        val out: String? = RegexTimeout.run(Callable { null }, 250L)
        assertNull(out)
    }

    @Test
    fun throwingWorkReturnsNullNotPropagated() {
        val out: String? = RegexTimeout.run(
            Callable { error("boom — should be swallowed by RegexTimeout") },
            250L,
        )
        assertNull(out)
    }

    @Test
    fun catastrophicBacktrackingIsAborted() {
        // Classic catastrophic backtracking case. Without
        // InterruptibleCharSequence + RegexTimeout this matcher
        // would spin for seconds. With them, the budget kicks in
        // and we get null in well under the budget.
        val pattern = Pattern.compile("(a+)+\$")
        val payload = "a".repeat(40) + "b" // forces the pathological path
        val started = System.currentTimeMillis()
        val out = RegexTimeout.run(
            Callable {
                val matcher = pattern.matcher(InterruptibleCharSequence(payload))
                if (matcher.find()) matcher.group() else null
            },
            150L, // tight budget — comfortably below the runtime explosion
        )
        val elapsed = System.currentTimeMillis() - started
        assertNull("matcher should have aborted, got '$out'", out)
        // Budget overhead has to stay reasonable. Generous ceiling
        // because Windows CI scheduler can stall briefly.
        assert(elapsed < 2_000L) { "match took $elapsed ms — budget did not bite" }
    }

    @Test
    fun normalMatcherFinishesUnderBudget() {
        val pattern = Pattern.compile("([0-9]{4,9})")
        val payload = "your code is 482915 — please use within 5 min"
        val out = RegexTimeout.run(
            Callable {
                val matcher = pattern.matcher(InterruptibleCharSequence(payload))
                if (matcher.find()) matcher.group(1) else null
            },
            250L,
        )
        assertEquals("482915", out)
        assertNotNull(out)
    }
}
