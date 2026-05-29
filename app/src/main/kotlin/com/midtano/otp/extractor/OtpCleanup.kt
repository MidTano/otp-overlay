// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import android.content.Context
import com.midtano.otp.data.Prefs

/**
 * Pre-extraction cleanup. Strips noise that the regex stage would
 * otherwise mistake for codes: domain names, "card ending NNNN"
 * fragments, the SMS Retriever `<#>` marker and quotes.
 *
 * Thin Context-aware wrapper around [OtpCleanupCore]; the JVM-level
 * regex / cache logic lives there so it can be unit-tested without
 * an Android runtime.
 */
internal object OtpCleanup {

    /**
     * Return the message body with every cleanup phrase removed. If
     * the feature is disabled the input is returned unchanged.
     */
    fun apply(ctx: Context, text: String?): String? {
        if (text == null) return null
        return OtpCleanupCore.apply(
            text = text,
            enabled = Prefs.isCleanupEnabled(ctx),
            phrases = Prefs.getCleanupPhrases(ctx),
        )
    }
}
