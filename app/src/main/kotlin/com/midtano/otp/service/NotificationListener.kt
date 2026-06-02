// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.midtano.otp.data.Prefs
import com.midtano.otp.extractor.OtpDeduplicator
import com.midtano.otp.extractor.OtpExtractor
import com.midtano.otp.system.CrashLogger
import com.midtano.otp.system.LastNotification
import com.midtano.otp.system.ScreenState

/**
 * Watches every posted notification, extracts OTP codes and
 * surfaces them through the overlay.
 *
 * By default the original notification is left alone — it stays in
 * the shade with its usual sound and heads-up. The user can opt
 * into removing the heads-up via [Prefs.isSilencePush]
 * (calls [cancelNotification]) or fall back to a silent re-post via
 * [Prefs.isHideHeadsUp] (handled by [NotificationMirror.repost]).
 *
 * [handlePosted] reads top-down as a sequence of single-purpose
 * guards; each records its own "skipped: …" verdict into
 * [LastNotification] so the diagnostic panel can explain exactly
 * why a given push did not surface a card.
 */
class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Only create the silent-mirror channel when the user has
        // the feature enabled. The channel surfaces as a toggle in
        // Android's per-app notification settings, so creating it
        // unconditionally would clutter the settings screen with a
        // category for a feature the user might never use.
        // SettingsActivity creates / deletes this channel as the
        // toggle flips, so the system view stays in sync.
        try {
            if (Prefs.isHideHeadsUp(this)) {
                NotificationMirror.ensureChannel(this)
            } else {
                NotificationMirror.deleteChannel(this)
            }
        } catch (e: Exception) {
            CrashLogger.logErr("NotificationListener.onListenerConnected channel sync failed", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Wholesale try/catch: a malformed notification (e.g. a VPN
        // dialog with a RemoteViews extras blob whose ClassLoader
        // is not available in our process) must never bring down
        // the listener — Android disables our service after one
        // uncaught exception, and the user must re-grant
        // notification access by hand.
        try {
            handlePosted(sbn)
        } catch (e: Exception) {
            CrashLogger.logErr("NotificationListener.handlePosted swallowed exception", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Aggressive OEM killers (MIUI / HyperOS / realme UI / ColorOS)
        // drop the NLS binder without rebinding even though the user
        // never revoked notification access. requestRebind is the
        // only documented way back without forcing them to re-toggle
        // the switch in system settings.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, NotificationListener::class.java))
                CrashLogger.log("NotificationListener: requestRebind issued")
            } catch (e: SecurityException) {
                CrashLogger.logErr("NotificationListener.requestRebind denied", e)
            } catch (e: IllegalStateException) {
                CrashLogger.logErr("NotificationListener.requestRebind refused", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* no-op */ }

    /**
     * Top-level dispatch. Reads as a sequence of guards, each
     * returning early with a `skipped: …` diagnostic on the first
     * reason the notification does not qualify, then handing off
     * to the overlay-dispatch tail.
     */
    private fun handlePosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!ScreenState.isAvailable(this)) return

        val pkg = prefilterPackage(sbn) ?: return
        val notif = prefilterNotification(pkg, sbn) ?: return
        val extras = readExtras(pkg, notif) ?: return
        val body = readBody(pkg, extras) ?: return
        val otp = runExtractor(pkg, body) ?: return

        if (suppressIfDuplicate(pkg, body, sbn, otp)) return

        applyHeadsUpSuppression(sbn)
        dispatchToOverlay(pkg, body, otp)
    }

    /**
     * Allow-list and ignore-prefix gate. Returns the package name on
     * pass, or `null` (with a "skipped" verdict recorded) on fail.
     */
    private fun prefilterPackage(sbn: StatusBarNotification): String? {
        val pkg = sbn.packageName ?: return null
        // Avoid handling our own foreground-service notification —
        // applicationId can differ between debug / flavour builds,
        // so use the runtime package.
        if (pkg == packageName) return null

        val ignoredPrefix = NotificationFilter.matchedIgnorePrefix(pkg)
        if (ignoredPrefix != null) {
            LastNotification.save(
                this,
                pkg,
                "",
                "skipped: ignored system package ($ignoredPrefix)",
            )
            return null
        }
        if (!Prefs.isPackageAllowed(this, pkg)) {
            LastNotification.save(
                this,
                pkg,
                "",
                "skipped: package filter is ON and this app isn't whitelisted",
            )
            return null
        }
        return pkg
    }

    /**
     * Notification-level gate (group summaries, foreground / ongoing
     * services). Returns the [Notification] on pass, or `null` on
     * skip with a recorded verdict.
     */
    private fun prefilterNotification(pkg: String, sbn: StatusBarNotification): Notification? {
        val notif = sbn.notification
        if (notif == null) {
            LastNotification.save(this, pkg, "", "skipped: notification null")
            return null
        }
        // Group-summary wrappers re-fire with the combined body
        // whenever any child changes; their text is a duplicate of
        // children we have already processed.
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            LastNotification.save(
                this,
                pkg,
                "",
                "skipped: group-summary notification (duplicate of children)",
            )
            return null
        }
        if (Prefs.isSkipForeground(this) &&
            (
                (notif.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
                (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
            )
        ) {
            LastNotification.save(
                this,
                pkg,
                "",
                "skipped: foreground / ongoing notification",
            )
            return null
        }
        return notif
    }

    /** Read the extras bundle, returning `null` on parcel-class mismatch. */
    private fun readExtras(pkg: String, notif: Notification): Bundle? {
        val extras = try {
            notif.extras
        } catch (t: Exception) {
            LastNotification.save(
                this,
                pkg,
                "",
                "skipped: cannot read extras (${t.javaClass.simpleName})",
            )
            return null
        }
        if (extras == null) {
            LastNotification.save(this, pkg, "", "skipped: extras null")
            return null
        }
        return extras
    }

    /**
     * Flatten extras into a single body string. Returns the body
     * or `null` on empty / unreadable extras.
     */
    private fun readBody(pkg: String, extras: Bundle): String? {
        val ex = NotificationExtrasReader.read(extras)
        if (!ex.ok) {
            // Cross-process span deserialisation failed; silent skip.
            return null
        }
        val body = NotificationBodyBuilder.build(
            ex.title,
            ex.text,
            ex.big,
            ex.sub,
            ex.summary,
            ex.info,
            ex.lines,
        )
        if (body.isEmpty()) {
            LastNotification.save(this, pkg, "", "skipped: all extras were empty")
            return null
        }
        return body
    }

    /** Run the OTP extractor, returning the matched code or `null`. */
    private fun runExtractor(pkg: String, body: String): String? {
        val otp = try {
            OtpExtractor.extract(this, body)
        } catch (t: Exception) {
            LastNotification.save(
                this,
                pkg,
                body,
                "extractor crashed: ${t.javaClass.simpleName}: ${t.message}",
            )
            return null
        }
        if (otp == null) {
            // Explain to the user exactly why the extractor said no.
            val hasKw = try {
                OtpExtractor.hasOtpKeyword(this, body)
            } catch (_: Exception) {
                false
            }
            val why = if (hasKw) {
                "no regex match (keyword present but no 4–9 digit run)"
            } else {
                "no trigger word in text (e.g. code / otp / verif…)"
            }
            LastNotification.save(this, pkg, body, "skipped: $why")
            return null
        }
        return otp
    }

    /**
     * Drop the duplicate dispatch but still re-apply heads-up
     * suppression, so a re-post of the same OTP cannot leak
     * through to a pop-up while the overlay queue already has the
     * code. Returns `true` if the notification was a duplicate
     * and the caller should stop.
     */
    private fun suppressIfDuplicate(
        pkg: String,
        body: String,
        sbn: StatusBarNotification,
        otp: String,
    ): Boolean {
        if (!OtpDeduplicator.isDuplicate(otp)) return false
        applyHeadsUpSuppression(sbn)
        LastNotification.save(
            this,
            pkg,
            body,
            "skipped: duplicate of recently shown code ($otp)",
        )
        return true
    }

    /**
     * Cancel the original heads-up notification or replace it with
     * a silent shade-only mirror, depending on user prefs.
     */
    private fun applyHeadsUpSuppression(sbn: StatusBarNotification) {
        if (Prefs.isSilencePush(this)) {
            runCatching { cancelNotification(sbn.key) }
        } else if (Prefs.isHideHeadsUp(this)) {
            runCatching { cancelNotification(sbn.key) }
            NotificationMirror.repost(this, sbn)
        }
    }

    /** Fire the SHOW_OTP intent at [OverlayService] and mark dedup on success. */
    private fun dispatchToOverlay(pkg: String, body: String, otp: String) {
        val sender = resolveAppLabel(pkg)
        val i = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_OTP
            putExtra(OverlayService.EXTRA_OTP, otp)
            putExtra(OverlayService.EXTRA_SENDER, sender)
            putExtra(OverlayService.EXTRA_SOURCE, OverlayService.SOURCE_PUSH)
            putExtra(OverlayService.EXTRA_PKG, pkg)
        }
        try {
            ContextCompat.startForegroundService(this, i)
            // Mark dedup only after the dispatch succeeded — a
            // failed FGS start would otherwise poison the dedup
            // window and suppress later sources of the same code.
            OtpDeduplicator.markShown(otp)
            LastNotification.save(this, pkg, body, "extracted $otp")
        } catch (t: SecurityException) {
            // SAW or FGS permission revoked between manifest install
            // and dispatch.
            LastNotification.save(
                this,
                pkg,
                body,
                "skipped: FGS dispatch denied (${t.javaClass.simpleName})",
            )
        } catch (t: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException /
            // MissingForegroundServiceTypeException — both
            // subclasses; fires when the app is in
            // background-restricted state at delivery.
            LastNotification.save(
                this,
                pkg,
                body,
                "skipped: FGS dispatch blocked (${t.javaClass.simpleName})",
            )
        }
    }

    private fun resolveAppLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }
}
