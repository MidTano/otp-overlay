// SPDX-License-Identifier: MIT
package com.midtano.otp.system

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.midtano.otp.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * SharedPreferences-backed log of recent notifications the listener
 * processed.
 *
 * Surfaces in the Settings → "Last notification" panel so the user
 * can see why an overlay did or did not fire without reaching for
 * adb / logcat. The buffer keeps the last [MAX_ENTRIES] reports;
 * each entry's text is truncated to [MAX_TEXT_CHARS] characters so a
 * single huge body cannot crowd out the rest of the history.
 *
 * ## OTP redaction
 *
 * Notification bodies and verdicts may contain the OTP value itself.
 * To honour the privacy promise that the value is never persisted,
 * every body and verdict is run through [redact] before storage:
 * digit runs are replaced with a `***N digits` marker so the
 * diagnostic still shows where the code sat and what length it was.
 */
internal object LastNotification {

    private const val FILE = "last_notif"
    private const val KEY = "log"

    /** How many notification reports to remember. */
    private const val MAX_ENTRIES = 30

    /**
     * Maximum character count of the combined notification body
     * stored per entry. Anything longer is truncated with an
     * ellipsis marker.
     */
    private const val MAX_TEXT_CHARS = 1000

    /**
     * Sentinel between ring-buffer records. Picked so it cannot
     * appear inside any normal notification body.
     */
    private const val ENTRY_SEP = "\u001E---\u001E"

    /**
     * Any 4..9 digit run is treated as a candidate OTP and masked.
     * Same length window the extractor uses
     * ([com.midtano.otp.extractor.OtpExtractor.DEFAULT_REGEX]) so the
     * redactor errs on the side of over-masking.
     */
    private val OTP_LIKE: Pattern = Pattern.compile("\\d{4,9}")

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Save a diagnostic report for one received notification. Body
     * and verdict are redacted before storage.
     *
     * Hot path: returns immediately and persists on [IoScope] so the
     * notification listener thread is not blocked on a SharedPrefs
     * write. The redaction itself runs synchronously up-front so the
     * raw OTP value never crosses a thread boundary into a
     * potentially long-lived coroutine.
     */
    fun save(ctx: Context, pkg: String?, text: String?, verdict: String?) {
        val safeText: String
        val originalLen: Int
        val safeVerdict: String
        try {
            var body = redact(text ?: "")
            originalLen = body.length
            if (originalLen > MAX_TEXT_CHARS) {
                body = body.substring(0, MAX_TEXT_CHARS) +
                    "…[truncated ${originalLen - MAX_TEXT_CHARS} chars]"
            }
            safeText = body
            safeVerdict = redact(verdict ?: "?")
        } catch (e: Exception) {
            CrashLogger.logErr("LastNotification.save: redaction failed", e)
            return
        }

        val safePkg = pkg
        val app = ctx.applicationContext
        IoScope.scope.launch {
            persist(app, safePkg, safeText, safeVerdict, originalLen)
        }
    }

    /**
     * Persist a pre-redacted report. Runs on [IoScope]; serialised
     * through the [LastNotification] singleton so a write from the
     * listener thread and a write from the overlay-service thread
     * cannot race a read-modify-write and lose an entry.
     */
    private fun persist(
        ctx: Context,
        pkg: String?,
        body: String,
        verdict: String,
        originalLen: Int,
    ) {
        try {
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            val entry = buildString {
                append(fmt.format(Date())).append("  ")
                append(pkg ?: "?").append('\n')
                append("Text (").append(originalLen).append(" chars):\n")
                append(body).append("\n\n")
                append("Verdict: ").append(verdict)
            }

            synchronized(this) {
                val existing = sp(ctx).getString(KEY, "") ?: ""
                val ring = split(existing)
                ring.addFirst(entry)
                while (ring.size > MAX_ENTRIES) ring.removeLast()
                sp(ctx).edit { putString(KEY, join(ring)) }
            }
        } catch (e: CancellationException) {
            // Cooperative cancellation must propagate up so the
            // scope owner decides whether to swallow it. Without
            // this re-throw, a future scoped IoScope refactor
            // would silently mask job-cancel as a "persist failed"
            // log line.
            throw e
        } catch (e: Exception) {
            CrashLogger.logErr("LastNotification.persist failed", e)
        }
    }

    /**
     * Return the full log (newest first) for display, or a
     * placeholder when no notifications have been processed yet.
     */
    fun read(ctx: Context): String {
        return try {
            val raw = sp(ctx).getString(KEY, null)
            if (raw.isNullOrEmpty()) {
                return ctx.getString(R.string.last_notif_empty)
            }
            val ring = split(raw)
            if (ring.isEmpty()) {
                return ctx.getString(R.string.last_notif_empty)
            }
            buildString {
                var i = 1
                val it = ring.iterator()
                while (it.hasNext()) {
                    if (i > 1) append("\n──────────────────────\n")
                    append("[#").append(i).append("] ").append(it.next())
                    i++
                }
            }
        } catch (t: Exception) {
            ctx.getString(R.string.last_notif_read_failed, t.javaClass.simpleName)
        }
    }

    /** Wipe the ring buffer. */
    fun clear(ctx: Context) {
        try {
            sp(ctx).edit { remove(KEY) }
        } catch (e: Exception) {
            CrashLogger.logErr("LastNotification.clear failed", e)
        }
    }

    /**
     * Replace every 4..9 digit run with a `***N digits` marker, and
     * additionally mask any contiguous 10+ digit run as a phone
     * number with `***N-digit-phone`.
     *
     * Phone-like runs are pre-masked first because notification
     * bodies persist on disk and the diagnostic panel renders them
     * back to the user verbatim — leaking even the last four
     * digits would defeat the redaction. The persisted form
     * preserves the run length but no actual digits.
     *
     * Defensive: any failure inside the redactor returns a marker
     * string so a regex-engine fault never causes the OTP value to
     * land on disk by accident. Marked `internal` so external code
     * cannot bypass the always-redact contract.
     */
    internal fun redact(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return try {
            // Pass 1: mask any 10+ digit run as a phone number
            // first, keeping only the last 4 digits. Otherwise the
            // OTP_LIKE pass would chop a phone number into one or
            // two "*** N digits" markers and the recognisable tail
            // would be lost.
            val phoneMasked = maskLongPhones(s)
            val m: Matcher = OTP_LIKE.matcher(phoneMasked)
            if (!m.find()) return phoneMasked
            val sb = StringBuffer(phoneMasked.length)
            do {
                val len = m.end() - m.start()
                m.appendReplacement(sb, Matcher.quoteReplacement("***$len digits"))
            } while (m.find())
            m.appendTail(sb)
            sb.toString()
        } catch (e: Exception) {
            CrashLogger.logErr("LastNotification.redact failed", e)
            "(redacted; redactor crashed)"
        }
    }

    /**
     * Pre-mask any 10+ digit run as a phone number with
     * `***N-digit-phone` (where N is the run length). Using a
     * non-numeric marker means the subsequent OTP_LIKE pass
     * cannot re-match the masked digits and shred the marker
     * into a confusing `***4 digits` substring.
     *
     * No last-N tail is preserved here because notification bodies
     * persist on disk and the diagnostic panel surfaces them — the
     * full short-code value would defeat the redaction. The caller
     * that DOES want a recognisable tail in a sandboxed log line
     * (e.g. [LogRedactor.redactSender] used by `SmsReceiver` /
     * `OverlayService`) lives outside the OTP_LIKE pipeline.
     */
    private fun maskLongPhones(s: String): String {
        val pat = Pattern.compile("\\d{10,}")
        val m = pat.matcher(s)
        if (!m.find()) return s
        val sb = StringBuffer(s.length)
        do {
            val match = m.group() ?: continue
            val len = match.length
            m.appendReplacement(sb, Matcher.quoteReplacement("***$len-digit-phone"))
        } while (m.find())
        m.appendTail(sb)
        return sb.toString()
    }

    private fun split(raw: String): ArrayDeque<String> {
        val out = ArrayDeque<String>()
        if (raw.isEmpty()) return out
        var from = 0
        while (true) {
            val idx = raw.indexOf(ENTRY_SEP, from)
            if (idx < 0) {
                val tail = raw.substring(from)
                if (tail.isNotEmpty()) out.addLast(tail)
                return out
            }
            out.addLast(raw.substring(from, idx))
            from = idx + ENTRY_SEP.length
        }
    }

    private fun join(ring: ArrayDeque<String>): String {
        if (ring.isEmpty()) return ""
        val sb = StringBuilder()
        var first = true
        for (s in ring) {
            if (!first) sb.append(ENTRY_SEP)
            sb.append(s)
            first = false
        }
        return sb.toString()
    }
}
