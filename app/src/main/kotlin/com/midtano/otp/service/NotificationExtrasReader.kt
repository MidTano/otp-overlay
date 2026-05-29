// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import android.app.Notification
import android.os.Bundle

/**
 * Pulls the seven text fields the OTP extractor needs out of a
 * [Notification.extras] bundle in one place.
 *
 * Some apps stick non-parcelable [CharSequence] spans into
 * notification extras; reading those across processes throws
 * because the source app's `ClassLoader` is not available in ours.
 * Centralising the read makes the failure path uniform and keeps
 * the listener service free of bundle-decode boilerplate.
 */
internal object NotificationExtrasReader {

    /** "Bundle could not be read" sentinel. */
    internal val FAILED = Extras(false, null, null, null, null, null, null, null)

    internal data class Extras(
        val ok: Boolean,
        val title: CharSequence?,
        val text: CharSequence?,
        val big: CharSequence?,
        val sub: CharSequence?,
        val summary: CharSequence?,
        val info: CharSequence?,
        val lines: Array<CharSequence>?,
    )

    /**
     * Read the standard set of `EXTRA_*` text fields. Returns a
     * "failed" instance ([Extras.ok] = false) if [bundle] is `null`
     * or any read throws.
     */
    fun read(bundle: Bundle?): Extras {
        if (bundle == null) return FAILED
        return try {
            Extras(
                ok = true,
                title = bundle.getCharSequence(Notification.EXTRA_TITLE),
                text = bundle.getCharSequence(Notification.EXTRA_TEXT),
                big = bundle.getCharSequence(Notification.EXTRA_BIG_TEXT),
                sub = bundle.getCharSequence(Notification.EXTRA_SUB_TEXT),
                summary = bundle.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
                info = bundle.getCharSequence(Notification.EXTRA_INFO_TEXT),
                lines = bundle.getCharSequenceArray(Notification.EXTRA_TEXT_LINES),
            )
        } catch (_: Exception) {
            // Cross-process span deserialisation failed. Caller
            // treats this the same as an empty notification.
            FAILED
        }
    }
}
