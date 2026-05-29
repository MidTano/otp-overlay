// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

/**
 * Marker exception thrown from [InterruptibleCharSequence.get] when
 * the matching thread sees its interrupt flag set. Caught by
 * [RegexTimeout.run] so a runaway user-supplied regex is aborted
 * without affecting the notification listener.
 */
internal class RegexTimeoutException :
    RuntimeException("regex match exceeded the configured budget")
