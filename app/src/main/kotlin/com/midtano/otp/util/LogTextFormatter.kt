// SPDX-License-Identifier: MIT
package com.midtano.otp.util

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.util.regex.Pattern

/**
 * Builds a coloured [SpannableStringBuilder] for log / debug panels.
 *
 * The palette is intentionally muted so it stays inside the
 * monochrome design language while still letting the eye snap to
 * errors and key values.
 */
internal object LogTextFormatter {

    /** Body text (dim grey). */
    const val COLOR_BASE: Int = 0xFFB8B8B8.toInt()

    /** Section titles (bright off-white + bold). */
    const val COLOR_HEADER: Int = 0xFFE6E6E6.toInt()

    /** Success markers ("extracted", "OK", "granted"). */
    const val COLOR_OK: Int = 0xFF8FBF7C.toInt()

    /** Warnings ("skipped", "no permission"). */
    const val COLOR_WARN: Int = 0xFFD8B870.toInt()

    /** Crash markers, "FAILED", exception names. */
    const val COLOR_ERROR: Int = 0xFFCF7A7A.toInt()

    /** Extracted OTP / numeric values. */
    const val COLOR_CODE: Int = 0xFF7FB3D5.toInt()

    /** Timestamps, metadata, divider rules. */
    const val COLOR_DIM: Int = 0xFF6F6F6F.toInt()

    private val TIMESTAMP = Pattern.compile("\\b\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?\\b")
    private val DATE_TIME =
        Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?\\b")
    private val HASH_RULE = Pattern.compile("(?m)^═+.+?═+$")
    private val PACKAGE = Pattern.compile("(?m)^\\s*\\d{2}:\\d{2}:\\d{2}\\s+(\\S+\\.\\S+)")
    private val EXTRACTED = Pattern.compile("(?im)\\bextracted\\s+(\\d{3,9})\\b")
    private val SKIPPED = Pattern.compile("(?im)^.*\\bskipped:.*$")
    private val CRASH_MARK = Pattern.compile(
        "(?im)\\b(?:===\\s*CRASH\\s*===|FATAL\\s*EXCEPTION|EXCEPTION|Caused by|FAILED|ERROR)\\b",
    )
    private val STACK_AT = Pattern.compile("(?m)^\\s*at\\s.+$")
    private val EXCEPTION_TYPE = Pattern.compile("\\b[A-Za-z][A-Za-z0-9_$]*Exception\\b")
    private val KV_LABEL = Pattern.compile(
        "(?m)^\\s*(time|thread|version|device|trigger|verdict|text|code|extracted)\\s*:",
    )
    private val OK_MARK =
        Pattern.compile("(?im)\\b(?:OK|granted|ready|success(?:fully)?|active|enabled)\\b")
    private val WARN_MARK =
        Pattern.compile("(?im)\\b(?:no permission|missing|not granted|not found|skipped)\\b")
    private val OTP_INLINE = Pattern.compile("(?im)(?:code|otp|verdict)\\s*:\\s*(\\d{3,9})\\b")

    /**
     * Convert raw log text into a coloured [SpannableStringBuilder].
     * The result is ready for `TextView.setText`.
     */
    fun format(raw: CharSequence?): SpannableStringBuilder {
        val out = if (raw == null) SpannableStringBuilder("") else SpannableStringBuilder(raw)
        if (out.isEmpty()) return out

        // Base colour for the body so untouched runs stay dim.
        out.setSpan(ForegroundColorSpan(COLOR_BASE), 0, out.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)

        applyPattern(out, HASH_RULE, COLOR_HEADER, bold = true)
        applyPattern(out, DATE_TIME, COLOR_DIM, bold = false)
        applyPattern(out, TIMESTAMP, COLOR_DIM, bold = false)
        applyGroup(out, PACKAGE, group = 1, COLOR_HEADER, bold = false)
        applyGroup(out, EXTRACTED, group = 1, COLOR_CODE, bold = true)
        applyGroup(out, OTP_INLINE, group = 1, COLOR_CODE, bold = true)
        applyPattern(out, KV_LABEL, COLOR_HEADER, bold = false)
        applyPattern(out, SKIPPED, COLOR_WARN, bold = false)
        applyPattern(out, OK_MARK, COLOR_OK, bold = true)
        applyPattern(out, WARN_MARK, COLOR_WARN, bold = false)
        applyPattern(out, EXCEPTION_TYPE, COLOR_ERROR, bold = true)
        applyPattern(out, STACK_AT, COLOR_DIM, bold = false)
        applyPattern(out, CRASH_MARK, COLOR_ERROR, bold = true)
        return out
    }

    private fun applyPattern(buf: SpannableStringBuilder, pat: Pattern, color: Int, bold: Boolean) {
        val m = pat.matcher(buf)
        while (m.find()) apply(buf, m.start(), m.end(), color, bold)
    }

    private fun applyGroup(
        buf: SpannableStringBuilder,
        pat: Pattern,
        group: Int,
        color: Int,
        bold: Boolean,
    ) {
        val m = pat.matcher(buf)
        while (m.find()) {
            val start = m.start(group)
            val end = m.end(group)
            if (start >= 0 && end > start) apply(buf, start, end, color, bold)
        }
    }

    private fun apply(buf: SpannableStringBuilder, start: Int, end: Int, color: Int, bold: Boolean) {
        buf.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold) {
            buf.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
