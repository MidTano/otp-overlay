// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import com.midtano.otp.system.CrashLogger
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Run a regex match on a worker thread under a hard wall-clock budget.
 *
 * The OTP extractor accepts a user-supplied regex from Settings, and
 * a pathological pattern (catastrophic backtracking, e.g. `(a+)+$` on
 * a long input) can spin a CPU forever. Because
 * [com.midtano.otp.service.NotificationListener] processes posted
 * notifications synchronously, that would hang the listener and
 * eventually trigger an ANR.
 *
 * [Thread.interrupt] alone does not abort an in-progress
 * [java.util.regex.Matcher]; the matcher must observe the interrupt
 * during one of its character reads — that is what
 * [InterruptibleCharSequence] is for.
 *
 * Workflow:
 * 1. Submit the matcher work to a small cached pool.
 * 2. Wait up to [BUDGET_MS] for the future.
 * 3. On timeout, interrupt the worker; the matcher's next read
 *    throws [RegexTimeoutException], which the worker re-raises as
 *    the future's cause.
 * 4. Cancel the future and return `null`.
 */
internal object RegexTimeout {

    /**
     * Hard ceiling on a single regex match. Healthy patterns on a
     * 4 KB notification body finish three orders of magnitude inside
     * this budget; a backtracking-pathological pattern is killed
     * well before the listener watchdog notices.
     */
    const val BUDGET_MS: Long = 250L

    private val pool = Executors.newCachedThreadPool(object : ThreadFactory {
        private val n = AtomicInteger()
        override fun newThread(r: Runnable): Thread =
            Thread(r, "otp-regex-budget-${n.incrementAndGet()}").apply { isDaemon = true }
    })

    /**
     * Run [work] under a wall-clock budget of [budgetMs]. Returns the
     * work's result, or `null` if the budget was exhausted, the work
     * threw, or the calling thread was itself interrupted while
     * waiting.
     */
    fun <T> run(work: Callable<T>, budgetMs: Long): T? {
        val future = pool.submit(work)
        return try {
            future.get(budgetMs, TimeUnit.MILLISECONDS)
        } catch (te: TimeoutException) {
            // Cancel-with-interrupt delivers Thread.interrupt() to the
            // executor thread; the InterruptibleCharSequence sees the
            // flag on its next read and unwinds the matcher.
            future.cancel(true)
            CrashLogger.log("OtpRegex: match exceeded $budgetMs ms budget — aborting")
            null
        } catch (ie: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            null
        } catch (ee: ExecutionException) {
            val cause = ee.cause
            if (cause is RegexTimeoutException) {
                CrashLogger.log("OtpRegex: match aborted by interrupt")
            } else {
                CrashLogger.logErr("OtpRegex: match threw", cause ?: ee)
            }
            null
        }
    }
}
