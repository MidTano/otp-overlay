// SPDX-License-Identifier: MIT
package com.midtano.otp.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Microbenchmarks for [com.midtano.otp.extractor.OtpDeduplicator].
 *
 * Sits on the `SmsReceiver` and `NotificationListener` hot paths
 * before any expensive extraction work. Both `isDuplicate` and
 * `markShown` need to stay sub-microsecond on a mid-range device
 * — anything slower would show up as listener latency in
 * production.
 */
@RunWith(AndroidJUnit4::class)
class DeduplicatorBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Before
    fun setUp() {
        BenchmarkSurface.clearDedupCache()
    }

    @After
    fun tearDown() {
        BenchmarkSurface.clearDedupCache()
    }

    @Test
    fun isDuplicate_emptyCache() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.isDuplicate("482915")
        }
    }

    @Test
    fun isDuplicate_hit() {
        BenchmarkSurface.markShown("482915")
        benchmarkRule.measureRepeated {
            BenchmarkSurface.isDuplicate("482915")
        }
    }

    @Test
    fun markShown_freshCode() {
        var i = 0
        benchmarkRule.measureRepeated {
            BenchmarkSurface.markShown("c%06d".format(i))
            i++
        }
    }
}
