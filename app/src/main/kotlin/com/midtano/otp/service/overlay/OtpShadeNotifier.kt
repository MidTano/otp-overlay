// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import androidx.core.app.NotificationCompat
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import com.midtano.otp.service.OverlayService
import com.midtano.otp.system.CrashLogger
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts each OTP as a standalone shade notification with its own
 * countdown timer.
 *
 * Always operates on the application context: the timers it posts
 * can outlive the caller (e.g. a Service that tore down between
 * [show] and the expiry callback) and we don't want the
 * application context replaced by something with a shorter
 * lifetime in the [active] map.
 */
internal object OtpShadeNotifier {

    const val CHANNEL_ID: String = "otp_shade"
    private const val NOTIF_ID_BASE = 0x4F540000

    const val ACTION_COPY: String = "com.midtano.otp.SHADE_COPY"
    const val ACTION_DISMISS: String = "com.midtano.otp.SHADE_DISMISS"
    const val EXTRA_OTP: String = "otp"
    const val EXTRA_NOTIF_ID: String = "notif_id"

    private const val TICK_MS: Long = 1_000L
    private val MAIN = Handler(Looper.getMainLooper())

    private val active = HashMap<Int, Active>()
    private val nextId = AtomicInteger(1)

    private class Active {
        var ticker: Runnable? = null
        var expiry: Runnable? = null
    }

    private fun appCtx(ctx: Context?): Context? {
        if (ctx == null) return null
        return ctx.applicationContext ?: ctx
    }

    fun ensureChannel(ctx: Context?) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val app = appCtx(ctx) ?: return
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            app.getString(R.string.shade_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = app.getString(R.string.shade_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    /**
     * Remove the shade channel from the system. Called when the
     * user switches the display mode away from SHADE so the
     * corresponding toggle does not linger in per-app notification
     * settings as dead UI. The next [ensureChannel] call recreates
     * it on demand.
     */
    fun deleteChannel(ctx: Context?) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val app = appCtx(ctx) ?: return
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.deleteNotificationChannel(CHANNEL_ID)
        } catch (_: SecurityException) {
            // No-op if the channel never existed or the
            // notification service is locked down on a managed
            // profile.
        }
    }

    /**
     * Show a silent shade-only notification with the OTP.
     *
     * `sender` — the human-readable app label (set as the
     * notification title so the shade can show the user *which*
     * app delivered the code).
     * `pkg`    — the source package, used as the notification group
     * key so multiple OTPs from the same app collapse together
     * instead of cluttering the shade.
     */
    fun show(
        ctx: Context,
        otp: String?,
        sender: String?,
        pkg: String?,
    ) {
        if (otp.isNullOrEmpty()) return
        val app = appCtx(ctx) ?: return
        ensureChannel(app)

        val durationMs = Prefs.getShadeDurationMs(app).toLong()
        val expiresAt = System.currentTimeMillis() + durationMs
        val notifId = NOTIF_ID_BASE or (nextId.getAndIncrement() and 0x0000FFFF)

        post(app, notifId, otp, sender, pkg, expiresAt)
        scheduleTicks(app, notifId, otp, sender, pkg, expiresAt)
        scheduleExpiry(app, notifId, expiresAt)
    }

    /** Cancel every active shade notification (used when leaving shade mode). */
    fun cancelActive(ctx: Context?) {
        val app = appCtx(ctx)
        val nm = app?.getSystemService(NotificationManager::class.java)
        synchronized(active) {
            for ((id, a) in active) {
                a.ticker?.let { MAIN.removeCallbacks(it) }
                a.expiry?.let { MAIN.removeCallbacks(it) }
                if (nm != null) {
                    try {
                        nm.cancel(id)
                    } catch (e: SecurityException) {
                        CrashLogger.logErr("shade cancelActive: nm.cancel denied", e)
                    }
                }
            }
            active.clear()
        }
    }

    fun onCopyAction(ctx: Context, otp: String?, notifId: Int) {
        if (otp.isNullOrEmpty()) return
        val app = appCtx(ctx) ?: return
        // Routes through the shared helper so the OTP carries the
        // EXTRA_IS_SENSITIVE flag on Android 13+ — without it the
        // clipboard banner echoes the digits back on screen.
        OverlayClipboard.copy(app, otp)
        finishOne(app, notifId)
    }

    fun onDismissAction(ctx: Context, notifId: Int) {
        finishOne(appCtx(ctx), notifId)
    }

    private fun finishOne(app: Context?, notifId: Int) {
        cancelTimers(notifId)
        if (app == null) return
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.cancel(notifId)
        } catch (e: SecurityException) {
            CrashLogger.logErr("shade finishOne: nm.cancel denied", e)
        }
    }

    private fun cancelTimers(notifId: Int) {
        synchronized(active) {
            val a = active.remove(notifId) ?: return
            a.ticker?.let { MAIN.removeCallbacks(it) }
            a.expiry?.let { MAIN.removeCallbacks(it) }
        }
    }

    private fun post(app: Context, notifId: Int, otp: String, sender: String?, pkg: String?, expiresAt: Long) {
        val remaining = maxOf(0L, expiresAt - System.currentTimeMillis())
        val seconds = ((remaining + 999L) / 1000L).toInt()
        val timer = formatTimer(seconds)

        val label = app.getString(R.string.shade_label_code)
        val body = buildBody(app, label, otp)

        val copyPi = pendingAction(app, notifId, otp, ACTION_COPY)
        val dismissPi = pendingAction(app, notifId, otp, ACTION_DISMISS)

        val title = if (sender.isNullOrBlank()) {
            app.getString(R.string.shade_title_default)
        } else {
            sender
        }
        // Per-source group so multiple OTPs from the same app
        // collapse together in the shade instead of stacking.
        val group = if (pkg.isNullOrBlank()) CHANNEL_ID else "$CHANNEL_ID:$pkg"

        val b = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(otp)
            .setSubText(timer)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(group)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(copyPi)
            .setDeleteIntent(dismissPi)
            .addAction(R.drawable.ic_copy, app.getString(R.string.shade_action_copy), copyPi)
            .addAction(R.drawable.ic_close, app.getString(R.string.shade_action_dismiss), dismissPi)

        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.notify(notifId, b.build())
        } catch (e: SecurityException) {
            // Notification access yanked while a shade entry is
            // live — fall through; the next post() will retry.
            CrashLogger.logErr("shade notify denied", e)
        }
    }

    /** Two-line body: dim "Found code" label on top, OTP digits below. */
    private fun buildBody(app: Context, label: String, otp: String): CharSequence {
        val sb = SpannableStringBuilder()
        val labelStart = sb.length
        sb.append(label)
        sb.setSpan(
            AbsoluteSizeSpan(spToPx(app, 13f)),
            labelStart,
            sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        sb.append('\n')

        val otpStart = sb.length
        sb.append(otp)
        sb.setSpan(
            AbsoluteSizeSpan(spToPx(app, 22f)),
            otpStart,
            sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        sb.setSpan(StyleSpan(Typeface.BOLD), otpStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(TypefaceSpan("monospace"), otpStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    private fun spToPx(app: Context, sp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, app.resources.displayMetrics).toInt()

    private fun scheduleTicks(app: Context, notifId: Int, otp: String, sender: String?, pkg: String?, expiresAt: Long) {
        val a = synchronized(active) { active.getOrPut(notifId) { Active() } }
        // Capture `a` so the runnable's identity check below
        // compares against a stable reference.
        val ticker = object : Runnable {
            override fun run() {
                if (!isStillActive(notifId, this)) return
                val remaining = expiresAt - System.currentTimeMillis()
                if (remaining <= 0L) return
                post(app, notifId, otp, sender, pkg, expiresAt)
                MAIN.postDelayed(this, TICK_MS)
            }
        }
        a.ticker = ticker
        MAIN.postDelayed(ticker, TICK_MS)
    }

    private fun isStillActive(notifId: Int, self: Runnable): Boolean {
        synchronized(active) {
            val a = active[notifId] ?: return false
            return a.ticker === self
        }
    }

    private fun scheduleExpiry(app: Context, notifId: Int, expiresAt: Long) {
        val a = synchronized(active) { active.getOrPut(notifId) { Active() } }
        val delay = maxOf(0L, expiresAt - System.currentTimeMillis())
        val expiry = Runnable {
            val nm = app.getSystemService(NotificationManager::class.java)
            if (nm != null) {
                try {
                    nm.cancel(notifId)
                } catch (e: SecurityException) {
                    CrashLogger.logErr("shade expiry: nm.cancel denied", e)
                }
            }
            cancelTimers(notifId)
        }
        a.expiry = expiry
        MAIN.postDelayed(expiry, delay)
    }

    private fun pendingAction(app: Context, notifId: Int, otp: String, action: String): PendingIntent {
        val i = Intent(app, OverlayService::class.java)
            .setAction(action)
            .putExtra(EXTRA_OTP, otp)
            .putExtra(EXTRA_NOTIF_ID, notifId)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val requestCode = notifId xor action.hashCode()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(app, requestCode, i, flags)
        } else {
            PendingIntent.getService(app, requestCode, i, flags)
        }
    }

    private fun formatTimer(seconds: Int): String {
        val s = maxOf(0, seconds)
        val m = s / 60
        val rem = s % 60
        return String.format(Locale.ROOT, "%02d:%02d", m, rem)
    }
}
