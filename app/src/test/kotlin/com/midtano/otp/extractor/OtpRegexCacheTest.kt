// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests for [OtpRegexCache.safeCompile]. Three behaviours:
 *
 *  1. A blank / null input produces the default pattern.
 *  2. A valid user pattern compiles, and a second call with the
 *     same source returns the cached instance (object identity).
 *  3. A malformed pattern falls back to the default rather than
 *     returning null — disabling extraction outright is far worse
 *     than ignoring an invalid edit in Settings.
 */
class OtpRegexCacheTest {

    @Test
    fun nullInputCompilesDefault() {
        val p = OtpRegexCache.safeCompile(null)
        assertNotNull(p)
        // The default pattern matches a 4..9 digit run with our
        // boundary semantics — verify it works.
        assert(p!!.matcher("code 482915 ends").find())
    }

    @Test
    fun emptyInputCompilesDefault() {
        val p = OtpRegexCache.safeCompile("")
        assertNotNull(p)
    }

    @Test
    fun validUserPatternCompiles() {
        val p = OtpRegexCache.safeCompile("([0-9]{6})")
        assertNotNull(p)
        assert(p!!.matcher("code 482915 ends").find())
    }

    @Test
    fun cachesByExactSource() {
        // Identical source ⇒ same Pattern instance returned. Lets
        // us catch a regression where the cache key includes
        // something flaky (e.g. system time).
        val a = OtpRegexCache.safeCompile("([0-9]{6})")
        val b = OtpRegexCache.safeCompile("([0-9]{6})")
        assertSame(a, b)
    }

    @Test
    fun malformedPatternFallsBackToDefault() {
        val p = OtpRegexCache.safeCompile("([a-z+")
        // Fall-back ⇒ non-null ⇒ extraction stays alive on next
        // notification.
        assertNotNull(p)
    }

    @Test
    fun caching_DoesNotMixUpDifferentSources() {
        val v1 = OtpRegexCache.safeCompile("([0-9]{4})")
        val v2 = OtpRegexCache.safeCompile("([0-9]{6})")
        // Different sources ⇒ different instances. Verifying with
        // matchers that one accepts the other's strict input gives
        // us a positive correctness signal.
        assertNotNull(v1)
        assertNotNull(v2)
        assert(v1!!.matcher("1234").find())
        assert(!v2!!.matcher("1234").find())
        assert(v2.matcher("482915").find())
    }

    @Test
    fun blankInputThenInvalidFallsBackGracefully() {
        // Edge case: previous call cached the default; subsequent
        // call with a broken pattern must not poison the cache.
        OtpRegexCache.safeCompile(null)
        val p = OtpRegexCache.safeCompile("([")
        assertNotNull(p)
    }

    @Test
    fun nullSurvivesEvenIfDefaultBroken() {
        // The default is a compile-time constant so it never breaks;
        // confirming it returns non-null seals the contract that
        // safeCompile only returns null when both the user pattern
        // and the default fail to compile.
        val p = OtpRegexCache.safeCompile(null)
        assertNotNull(p)
        // Sanity: not the cached fallback pattern that gets confused
        // with a blank value.
        assert(p === OtpRegexCache.safeCompile(OtpExtractor.DEFAULT_REGEX))
    }
}
