// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

/**
 * Single source of truth for "where did this OTP come from" across
 * the receiver, overlay and stats path.
 *
 * The [storageId] is the value persisted in stats and routed through
 * intent extras; it is intentionally lower-case so the stats
 * comparator and the overlay writer agree on case.
 */
internal enum class OtpSource(
    val storageId: String,
) {
    SMS("sms"),
    PUSH("push"),
    TEST("test"),
    ;

    companion object {
        /**
         * Decode a stored value. Lookup is case-insensitive so a
         * tolerant comparison handles any future enum order changes.
         */
        fun fromStorageId(value: String?): OtpSource? {
            if (value.isNullOrEmpty()) return null
            val lower = value.lowercase()
            return entries.firstOrNull { it.storageId == lower }
        }
    }
}
