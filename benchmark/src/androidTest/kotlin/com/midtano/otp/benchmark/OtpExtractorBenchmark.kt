// SPDX-License-Identifier: MIT
package com.midtano.otp.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Microbenchmarks for the OTP extraction hot path.
 *
 * Notes on stability:
 *   • Run on a phone in airplane mode + screen on so background
 *     workers don't compete for the big core.
 *   • The `BenchmarkRule` framework auto-detects sustained
 *     performance throttling and warns the operator; warnings
 *     don't fail the run, but they reduce the trustworthiness of
 *     the absolute numbers. The relative regression signal stays
 *     usable either way.
 *   • Numbers are device-specific. The baseline checked in lives
 *     under `benchmark/baseline/<device>.json`; CI compares to
 *     the matching device file.
 */
@RunWith(AndroidJUnit4::class)
class OtpExtractorBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val realisticEnglishSms = "Your verification code is 482915. Do not share."
    private val realisticRussianSms =
        "Ваш одноразовый код: 384712. Не сообщайте никому."
    private val noiseBody =
        "Welcome to MyBank! Your account balance is 12345 USD. " +
            "Statement closes on 31 Dec. Customer support: +1-555-0100."

    @Test
    fun extract_typicalEnglishOtp() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.extractOtp(ctx, realisticEnglishSms)
        }
    }

    @Test
    fun extract_typicalRussianOtp() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.extractOtp(ctx, realisticRussianSms)
        }
    }

    @Test
    fun extract_noTriggerKeyword_earlyReject() {
        // No trigger keyword in the body — the extractor takes the
        // fast-reject path. This benchmarks the "bystander
        // notification" cost: every push the listener sees that's
        // not an OTP must be cheap.
        benchmarkRule.measureRepeated {
            BenchmarkSurface.extractOtp(ctx, noiseBody)
        }
    }

    @Test
    fun extract_emptyBody_noOpFastPath() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.extractOtp(ctx, "")
        }
    }

    @Test
    fun hasOtpKeyword_typicalBody() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.hasOtpKeyword(ctx, realisticEnglishSms)
        }
    }

    @Test
    fun hasOtpKeyword_noKeywordBody() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.hasOtpKeyword(ctx, noiseBody)
        }
    }
}
