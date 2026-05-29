// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

/**
 * How an extracted OTP is surfaced to the user.
 *
 * The [storageId] is the on-disk integer that [PrefsCore] persists,
 * so new entries must be appended at the end to preserve compatibility
 * with already-saved values.
 *
 * - [OVERLAY] — full-screen reveal card with chromatic glow (default).
 * - [SHADE]   — silent system-shade notification with copy and dismiss
 *               actions; preferred when an overlay would steal focus.
 */
enum class DisplayMode {
    OVERLAY,
    SHADE,
    ;

    /** Persisted ordinal written to disk by [PrefsCore]. */
    val storageId: Int get() = ordinal

    companion object {
        /** Default applied when nothing has been stored yet. */
        val DEFAULT: DisplayMode = OVERLAY

        /** Decode an integer from disk, clamping to the known range. */
        fun fromStorageId(value: Int): DisplayMode {
            val values = entries
            return values[value.coerceIn(0, values.size - 1)]
        }
    }
}
