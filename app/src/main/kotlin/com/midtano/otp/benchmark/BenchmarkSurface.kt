// SPDX-License-Identifier: MIT
package com.midtano.otp.benchmark

import android.content.Context
import com.midtano.otp.extractor.OtpDeduplicator
import com.midtano.otp.extractor.OtpExtractor
import com.midtano.otp.extractor.OtpRegexCache
import com.midtano.otp.service.overlay.OverlayQueue
import com.midtano.otp.service.overlay.PendingShow
import com.midtano.otp.system.LastNotification
import com.midtano.otp.system.LogRedactor

/**
 * Public re-export of the hot paths that the `:benchmark` module
 * measures.
 *
 * Marked `public` rather than `internal` because the benchmark
 * module sits in a separate Gradle project (Kotlin's `internal`
 * stops at the module boundary). Each forwarder is a one-liner so
 * R8 inlines them away in the release build — production callers
 * still go straight to the underlying internals without any extra
 * indirection.
 *
 * **Do not** call this surface from production code. It exists
 * solely as a controlled point of entry for the benchmark suite,
 * and growing it puts internal-only refactors at risk.
 */
object BenchmarkSurface {

    // ── extractor pipeline ──────────────────────────────────────

    fun extractOtp(ctx: Context, text: String?): String? =
        OtpExtractor.extract(ctx, text)

    fun hasOtpKeyword(ctx: Context, text: String?): Boolean =
        OtpExtractor.hasOtpKeyword(ctx, text)

    fun compileRegex(src: String): java.util.regex.Pattern? =
        OtpRegexCache.safeCompile(src)

    // ── dedup ───────────────────────────────────────────────────

    fun isDuplicate(otp: String): Boolean =
        OtpDeduplicator.isDuplicate(otp)

    fun markShown(otp: String) =
        OtpDeduplicator.markShown(otp)

    fun clearDedupCache() =
        OtpDeduplicator.clearForTest()

    // ── redactor ────────────────────────────────────────────────

    fun redactNotification(s: String?): String =
        LastNotification.redact(s)

    fun redactSender(s: String?): String =
        LogRedactor.redactSender(s)

    fun redactDigits(s: String?): String =
        LogRedactor.redactDigits(s)

    // ── queue ───────────────────────────────────────────────────

    fun newQueue(): OverlayQueue = OverlayQueue()

    fun queueOffer(q: OverlayQueue, otp: String, currentOtp: String?): Boolean =
        q.offer(PendingShow(otp, null, "test", null), currentOtp)

    fun queuePollFirst(q: OverlayQueue): String? =
        q.pollFirst()?.otp
}
