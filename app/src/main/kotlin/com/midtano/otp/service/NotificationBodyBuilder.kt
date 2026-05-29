// SPDX-License-Identifier: MIT
package com.midtano.otp.service

/**
 * Pure-function builder that flattens a notification's individual
 * extras (title, text, big-text, sub, summary, info, line array)
 * into the single body string the OTP extractor scans.
 *
 * Sits outside [NotificationListener] so it can be unit tested
 * directly. Any `null` field is skipped, every surviving field is
 * followed by a single space, and the final result is trimmed.
 */
internal object NotificationBodyBuilder {

    /**
     * Build a flat body string from the standard [android.app.Notification]
     * extras.
     *
     * @return trimmed body, or empty string if every input was null
     *         or empty.
     */
    fun build(
        title: CharSequence?,
        text: CharSequence?,
        big: CharSequence?,
        sub: CharSequence?,
        summary: CharSequence?,
        info: CharSequence?,
        lines: Array<CharSequence>?,
    ): String {
        val out = StringBuilder()
        appendIfPresent(out, title)
        appendIfPresent(out, text)
        appendIfPresent(out, big)
        appendIfPresent(out, sub)
        appendIfPresent(out, summary)
        appendIfPresent(out, info)
        if (lines != null) for (l in lines) appendIfPresent(out, l)
        return out.toString().trim()
    }

    private fun appendIfPresent(out: StringBuilder, value: CharSequence?) {
        if (value.isNullOrEmpty()) return
        out.append(value).append(' ')
    }
}
