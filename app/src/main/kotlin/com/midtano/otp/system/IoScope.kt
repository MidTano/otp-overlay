// SPDX-License-Identifier: MIT
package com.midtano.otp.system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide background scope for short, non-cancellable disk
 * writes (stats updates, diagnostic ring buffer flushes).
 *
 * Lives at the system layer so any data-store writer can route an
 * IO-bound operation off the receiver / listener thread without
 * having to declare its own [CoroutineScope]. [SupervisorJob] keeps
 * one failing write from cancelling the rest.
 *
 * Callers must:
 *  - keep work short and idempotent — there is no cancellation hook,
 *  - swallow their own exceptions and log via [CrashLogger]; an
 *    uncaught throwable here will surface in logcat but never bring
 *    the process down.
 */
internal object IoScope {

    val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
