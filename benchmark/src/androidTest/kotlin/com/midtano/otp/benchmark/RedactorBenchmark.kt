// SPDX-License-Identifier: MIT
package com.midtano.otp.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Microbenchmarks for [com.midtano.otp.system.LogRedactor] and
 * [com.midtano.otp.system.LastNotification.redact].
 *
 * Privacy-critical path: every notification body and sender label
 * goes through the redactor before reaching the rolling
 * diagnostic file. p99 matters because the listener thread is
 * blocked on the redactor's regex pass; a regression that pushes
 * p99 over a few milliseconds would make the overlay noticeably
 * laggy on a burst of pushes.
 */
@RunWith(AndroidJUnit4::class)
class RedactorBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val singleOtpBody =
        "Your verification code is 482915. Do not share with anyone."
    private val multipleOtpsBody =
        "Codes: 1234, 567890, 12345678, 987 654 321 — all expire in 5 minutes."
    private val phoneBody =
        "From +71234567890: your code is 482915. " +
            "Customer support is +18005551234, account 9876543210 expires soon."
    private val plainProse =
        "No codes here, just a message about your appointment at 12:30 tomorrow."

    @Test
    fun lastNotificationRedact_singleOtp() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.redactNotification(singleOtpBody)
        }
    }

    @Test
    fun lastNotificationRedact_multipleOtpsAndPhones() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.redactNotification(phoneBody)
        }
    }

    @Test
    fun lastNotificationRedact_multipleOtpsOnly() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.redactNotification(multipleOtpsBody)
        }
    }

    @Test
    fun lastNotificationRedact_proseFastPath() {
        // The redactor short-circuits on bodies with no
        // OTP-shaped digit run — this benchmarks the
        // bystander-notification cost.
        benchmarkRule.measureRepeated {
            BenchmarkSurface.redactNotification(plainProse)
        }
    }

    @Test
    fun logRedactor_redactSender_phoneNumber() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.redactSender("+71234567890")
        }
    }

    @Test
    fun logRedactor_redactSender_brandLabel() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.redactSender("Sberbank")
        }
    }

    @Test
    fun logRedactor_redactDigits_typical() {
        benchmarkRule.measureRepeated {
            BenchmarkSurface.redactDigits(singleOtpBody)
        }
    }
}
