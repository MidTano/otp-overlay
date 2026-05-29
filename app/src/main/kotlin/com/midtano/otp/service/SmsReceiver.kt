// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.midtano.otp.extractor.OtpDeduplicator
import com.midtano.otp.extractor.OtpExtractor
import com.midtano.otp.system.CrashLogger
import com.midtano.otp.system.LogRedactor
import com.midtano.otp.system.ScreenState

/**
 * Receives `SMS_RECEIVED`, concatenates multi-part messages,
 * extracts an OTP, and asks [OverlayService] to display it.
 *
 * The source SMS app's notification is left alone — Android still
 * files it in the system tray; we just surface a friendlier overlay
 * on top.
 *
 * Uses [Telephony.Sms.Intents.getMessagesFromIntent] which handles
 * multi-part concatenation, format detection (3GPP vs 3GPP2) and
 * PDU framing on every device the platform supports.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return
        if (!ScreenState.isAvailable(context)) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: RuntimeException) {
            // OEM ROMs that ship malformed SMS broadcast intents
            // typically wrap the underlying parse failure in a
            // RuntimeException (NPE / IllegalArgumentException /
            // ArrayIndexOutOfBoundsException). Catching the parent
            // covers the family without swallowing genuine bugs
            // outside this single call.
            CrashLogger.logErr("getMessagesFromIntent failed", e)
            return
        }
        if (messages == null || messages.isEmpty()) return

        val body = StringBuilder()
        var sender = ""
        for (msg in messages) {
            if (msg == null) continue
            msg.messageBody?.let { body.append(it) }
            if (sender.isEmpty()) {
                msg.displayOriginatingAddress?.let { sender = it }
            }
        }

        val otp = OtpExtractor.extract(context, body.toString()) ?: return
        if (OtpDeduplicator.isDuplicate(otp)) return

        val i = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_OTP
            putExtra(OverlayService.EXTRA_OTP, otp)
            putExtra(OverlayService.EXTRA_SENDER, sender)
            putExtra(OverlayService.EXTRA_SOURCE, OverlayService.SOURCE_SMS)
        }

        // Mark the OTP as "shown" only after the dispatch succeeded.
        // Marking before the call would poison the dedup window if
        // startForegroundService fails (background restrictions,
        // FGS-not-allowed, …); a push mirror arriving 200 ms later
        // would then be silently suppressed even though the user
        // never saw the overlay.
        try {
            ContextCompat.startForegroundService(context, i)
            OtpDeduplicator.markShown(otp)
            CrashLogger.log("SMS otp dispatched: sender=${LogRedactor.redactSender(sender)}")
        } catch (e: SecurityException) {
            // Broadcast receiver was active but SYSTEM_ALERT_WINDOW
            // was revoked between registration and delivery.
            CrashLogger.logErr("SMS startForegroundService denied", e)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is
            // a subclass of IllegalStateException — fires when the
            // app is in a background-restricted state at delivery.
            CrashLogger.logErr("SMS startForegroundService blocked", e)
        }
    }
}
