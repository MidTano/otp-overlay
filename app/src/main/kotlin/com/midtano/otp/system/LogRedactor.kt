// SPDX-License-Identifier: MIT
package com.midtano.otp.system

import java.util.regex.Pattern

/**
 * Redact PII before it lands in the rolling diagnostic log.
 *
 * Two redactors are exposed because two different shapes of
 * sensitive data flow into [CrashLogger]:
 *
 *  - [redactSender] — phone numbers, short-codes, free-form
 *    sender labels that may include a phone number or a name.
 *    Phone-like digit runs (>= 4 digits) are kept only as their
 *    last four characters. Free-form labels are passed through —
 *    a brand name like "Sberbank" is not personal data on its own.
 *
 *  - [redactDigits] — generic OTP-shaped digit runs (4..9 digits)
 *    inside an arbitrary string. Same window as
 *    [LastNotification.redact]; kept here so any future logging
 *    site outside [LastNotification] has a single place to call.
 *
 * Both functions are pure and never throw — a regex-engine fault
 * returns the original string with the sensitive substring replaced
 * by a generic marker so failure to redact never causes the OTP or
 * the phone number to land on disk by accident.
 */
internal object LogRedactor {

    /** Phone / short-code: any contiguous run of 4+ digits. */
    private val PHONE_LIKE: Pattern = Pattern.compile("\\d{4,}")

    /** Same window as [LastNotification.OTP_LIKE]. */
    private val OTP_LIKE: Pattern = Pattern.compile("\\d{4,9}")

    /** Placeholder used when the redactor itself fails. */
    private const val FAILSAFE: String = "***"

    /**
     * Redact sender / package / address labels for diagnostic logs.
     *
     * Replaces each contiguous run of 4+ digits with `***NNNN`,
     * keeping only the last four digits — enough for the user to
     * recognise their bank's short-code in the log without exposing
     * the full number to any third party who later reads the file.
     *
     * Returns the empty string for `null` / blank input.
     */
    fun redactSender(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return try {
            val m = PHONE_LIKE.matcher(s)
            if (!m.find()) return s
            val sb = StringBuffer(s.length)
            do {
                val match = m.group() ?: continue
                val tail = if (match.length <= 4) match else match.takeLast(4)
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("***$tail"))
            } while (m.find())
            m.appendTail(sb)
            sb.toString()
        } catch (_: Exception) {
            FAILSAFE
        }
    }

    /**
     * Redact OTP-shaped digit runs (4..9 digits) inside an arbitrary
     * string. Kept aligned with [LastNotification.redact] so a
     * future logging site outside [LastNotification] has a single
     * place to call.
     */
    fun redactDigits(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return try {
            val m = OTP_LIKE.matcher(s)
            if (!m.find()) return s
            val sb = StringBuffer(s.length)
            do {
                val len = m.end() - m.start()
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("***$len digits"))
            } while (m.find())
            m.appendTail(sb)
            sb.toString()
        } catch (_: Exception) {
            FAILSAFE
        }
    }
}
