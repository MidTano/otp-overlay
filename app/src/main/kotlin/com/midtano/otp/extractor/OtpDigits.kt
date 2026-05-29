// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

/**
 * Converts Arabic-Indic and Persian digit sequences to ASCII 0..9 so
 * downstream code (regex, clipboard, accessibility paste) sees a
 * uniform numeric form regardless of the source script.
 */
internal object OtpDigits {

    fun normalize(input: String?): String? {
        if (input.isNullOrEmpty()) return input
        var changed = false
        val out = input.toCharArray()
        for (i in out.indices) {
            val ch = out[i]
            when (ch) {
                in '\u0660'..'\u0669' -> {
                    out[i] = '0' + (ch - '\u0660')
                    changed = true
                }
                in '\u06F0'..'\u06F9' -> {
                    out[i] = '0' + (ch - '\u06F0')
                    changed = true
                }
                else -> Unit
            }
        }
        return if (changed) String(out) else input
    }
}
