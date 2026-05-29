// SPDX-License-Identifier: MIT
package com.midtano.otp.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Microbenchmarks for [com.midtano.otp.extractor.OtpRegexCache].
 *
 * The cache exists because a fresh `Pattern.compile` on the
 * default OTP regex costs ~50× a cached lookup on a typical
 * device. The benchmark proves the cache earns its keep — and
 * a future refactor that quietly invalidates it on every call
 * would surface as an immediate ~50× regression in
 * `safeCompile_cachedPattern`.
 */
@RunWith(AndroidJUnit4::class)
class RegexCacheBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val defaultRegex = "(?<![A-Za-zА-Яа-я0-9])([0-9]{4,9})(?![A-Za-zА-Яа-я0-9])"

    @Test
    fun safeCompile_cachedPattern() {
        // Warm the cache once before the timed loop.
        BenchmarkSurface.compileRegex(defaultRegex)
        benchmarkRule.measureRepeated {
            BenchmarkSurface.compileRegex(defaultRegex)
        }
    }

    @Test
    fun safeCompile_unknownPattern() {
        // Unique source on every call would force a recompile.
        // Use a small set of patterns that rotate so the cache
        // keeps thrashing — measures the pessimistic case where a
        // user pastes a different regex into Settings every time.
        val patterns = arrayOf(
            "(\\d{4})",
            "(\\d{6})",
            "(\\d{8})",
            "(\\d{4,6})",
            "(\\d{6,8})",
        )
        var i = 0
        benchmarkRule.measureRepeated {
            BenchmarkSurface.compileRegex(patterns[i])
            i = (i + 1) % patterns.size
        }
    }

    @Test
    fun safeCompile_invalidPattern_fallback() {
        // Broken regex source — the cache must fall back to the
        // default without throwing.
        benchmarkRule.measureRepeated {
            BenchmarkSurface.compileRegex("([")
        }
    }
}
