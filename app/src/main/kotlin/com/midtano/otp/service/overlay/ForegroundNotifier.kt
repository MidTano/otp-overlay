// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.midtano.otp.R
import com.midtano.otp.ui.main.MainActivity

/**
 * Builds the silent placeholder channel and notification that back
 * the foreground-service requirement: `IMPORTANCE_LOW`, `SECRET`
 * lockscreen visibility, no badge, no sound, no vibration, MIN
 * priority.
 *
 * Android forces every foreground service to surface a notification.
 * The combination of choices below pushes the placeholder as far
 * out of the user's view as the platform allows:
 *
 *  - `PRIORITY_MIN` keeps it folded under the "Silent" group;
 *  - `setShowBadge(false)` and `VISIBILITY_SECRET` keep the launcher
 *    icon and lockscreen clean;
 *  - `FOREGROUND_SERVICE_DEFERRED` lets the service finish inside
 *    the 10-second grace window without surfacing anything at all;
 *  - [com.midtano.otp.service.OverlayService.maybeStopForeground]
 *    drops the FGS as soon as the queue empties, so a typical
 *    overlay (~7 s) never produces a tray entry.
 *
 * On the rare burst path where multiple OTPs queue up the
 * placeholder does become visible, so it carries a real title /
 * body / launcher icon (instead of the default empty
 * `ic_dialog_info`, which renders as a context-less `!` on most
 * Android themes).
 */
internal object ForegroundNotifier {

    const val FG_CHANNEL_ID: String = "overlay_channel"
    const val FG_NOTIF_ID: Int = 1

    /** Lazily create the FGS channel. Idempotent. */
    fun createChannel(ctx: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        // IMPORTANCE_LOW is the lowest tier the system accepts for
        // an FGS-bearing channel; combined with setShowBadge(false)
        // and the priority bits below this keeps the placeholder
        // notification collapsed under "Silent" instead of pinning
        // a full-width row to the drawer.
        val ch = NotificationChannel(
            FG_CHANNEL_ID,
            ctx.getString(R.string.fg_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(ch)
    }

    /**
     * Build the silent placeholder notification used to back the
     * foreground-service requirement. Tapping it opens [MainActivity].
     *
     * `setForegroundServiceBehavior(FOREGROUND_SERVICE_DEFERRED)`
     * tells Android to defer surfacing this notification by 10 s.
     * The overlay's typical lifetime is ~10–11 s (auto-copy timer
     * + dismiss flourish), and `OverlayService.maybeStopForeground`
     * drops the FGS as soon as the queue empties. Combined, the
     * placeholder is invisible in the common case — the user only
     * sees it on the rare path where multiple OTPs queue up and
     * the service runs longer than the deferral window.
     */
    fun build(ctx: Context): Notification {
        val pi = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // Minimal chrome — MIN priority, silent, hidden from
        // lockscreen — the most suppressed shape Android allows
        // for a mandatory FGS notification. Title/text/icon are
        // populated so that on the rare burst path where the
        // placeholder does surface it looks like a normal silent
        // notification rather than an empty `!` row.
        return NotificationCompat.Builder(ctx, FG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(ctx.getString(R.string.fg_notification_title))
            .setContentText(ctx.getString(R.string.fg_notification_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            // 10-second grace period before the placeholder is
            // shown. Short-lived foreground services that finish
            // within the window never surface a tray entry.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }
}
