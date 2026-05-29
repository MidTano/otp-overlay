// SPDX-License-Identifier: MIT
package com.midtano.otp.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Microbenchmarks for [com.midtano.otp.service.overlay.OverlayQueue].
 *
 * `offer` is called from the main thread on every incoming push
 * during a burst (bank + push mirror + carrier alert all fire
 * within ~200 ms). The queue has a hard cap of 2000; we
 * benchmark both the empty-queue fast path and a half-loaded
 * queue to surface any O(n) tail in the dedup `contains` scan.
 */
@RunWith(AndroidJUnit4::class)
class OverlayQueueBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun offer_emptyQueue_freshOtp() {
        benchmarkRule.measureRepeated {
            // `runWithMeasurementDisabled` is the official way to
            // exclude setup from the timed window without polluting
            // the hot path. Each iteration starts with a fresh
            // queue and a unique OTP so there's no dedup short-
            // circuit.
            val q = runWithMeasurementDisabled { BenchmarkSurface.newQueue() }
            BenchmarkSurface.queueOffer(q, "111111", null)
        }
    }

    @Test
    fun offer_halfLoadedQueue() {
        benchmarkRule.measureRepeated {
            val q = runWithMeasurementDisabled {
                val q = BenchmarkSurface.newQueue()
                for (i in 0 until 1000) {
                    BenchmarkSurface.queueOffer(q, "c%05d".format(i), null)
                }
                q
            }
            BenchmarkSurface.queueOffer(q, "newest", null)
        }
    }

    @Test
    fun offer_duplicateRejection() {
        benchmarkRule.measureRepeated {
            val q = runWithMeasurementDisabled {
                val q = BenchmarkSurface.newQueue()
                BenchmarkSurface.queueOffer(q, "111111", null)
                q
            }
            BenchmarkSurface.queueOffer(q, "111111", null)
        }
    }

    @Test
    fun pollFirst_fastPath() {
        benchmarkRule.measureRepeated {
            val q = runWithMeasurementDisabled {
                val q = BenchmarkSurface.newQueue()
                BenchmarkSurface.queueOffer(q, "111111", null)
                q
            }
            BenchmarkSurface.queuePollFirst(q)
        }
    }
}
