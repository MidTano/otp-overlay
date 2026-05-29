// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.midtano.otp.R
import com.midtano.otp.system.CrashLogger

/**
 * Re-posts an OTP-carrying notification as a silent shade-only
 * clone so the source app's heads-up does not flash even after the
 * overlay surfaces.
 *
 * Why a clone instead of a plain `cancelNotification`:
 * - cancel removes the entry from the shade as well, so the user
 *   loses the trail entirely;
 * - many apps re-post their notification a moment later, so the
 *   pop-up keeps coming back.
 *
 * What this object does instead:
 * 1. The caller cancels the original (kills the heads-up, the
 *    sound and any stale clones from previous reposts).
 * 2. We re-post our own copy on a dedicated low-importance
 *    [MIRROR_CHANNEL_ID] channel so it lands quietly in the shade
 *    only — no heads-up, no sound, no vibration.
 *
 * Branding is preserved as much as possible without spoofing: the
 * source app's launcher icon as the large icon, its label as the
 * title, and its original [PendingIntent] (when any) is reused so
 * a tap still opens the source app. The small icon must come from
 * our own package — we use the generic bell so the user
 * recognises the clone as ours and can long-press to disable the
 * mirror channel if they ever want heads-up back.
 */
internal object NotificationMirror {

    /** Channel id for the silent shade-only mirror. `IMPORTANCE_LOW` = no heads-up. */
    const val MIRROR_CHANNEL_ID: String = "otp_shade_mirror"

    /**
     * Stable notification id for the silent mirror, derived from
     * the source package and notification key. Each unique
     * notification gets its own slot so multiple codes from the
     * same app don't overwrite each other.
     */
    private fun mirrorId(pkg: String?, key: String?): Int {
        val seed = "${pkg.orEmpty()}|${key.orEmpty()}"
        return 0x4F000000 or (seed.hashCode() and 0x00FFFFFF)
    }

    /** Ensure the silent mirror channel exists. Safe to call repeatedly. */
    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(MIRROR_CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            MIRROR_CHANNEL_ID,
            ctx.getString(R.string.mirror_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = ctx.getString(R.string.mirror_channel_description)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    /**
     * Remove the silent mirror channel from the system. Called
     * when the user disables the heads-up suppression preference
     * so the corresponding toggle vanishes from the per-app
     * notification settings instead of lingering as dead UI.
     *
     * The next [ensureChannel] call will recreate it with the
     * default settings; any user-customised importance the user
     * had set on the channel is reset, which is the explicit
     * expectation when an Android channel is deleted.
     */
    fun deleteChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.deleteNotificationChannel(MIRROR_CHANNEL_ID)
        } catch (_: Exception) {
            // No-op if the channel never existed.
        }
    }

    /**
     * Re-post [sbn] as a silent shade-only clone. The caller must
     * have already cancelled the original. Failures are swallowed:
     * the silent mirror is a polish feature and must never bring
     * down the listener.
     */
    fun repost(ctx: Context, sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            ensureChannel(ctx)

            val src = sbn.notification ?: return
            val extras = src.extras

            val pkg: String? = sbn.packageName
            val appLabel: CharSequence = resolveAppLabel(ctx, pkg)

            var title = extras?.getCharSequence(Notification.EXTRA_TITLE)
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)
            val big = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            if (title.isNullOrEmpty()) title = appLabel

            val largeIconBmp: Bitmap? = resolveAppIcon(ctx, pkg)?.let(::drawableToBitmap)

            // Tap reuses the source notification's intent when one
            // is present, so the user opens the same screen as
            // they would have from the original push. Falls back
            // to the launcher intent so the user always has
            // somewhere to go.
            val contentIntent: PendingIntent? = src.contentIntent ?: launcherIntentFor(ctx, pkg)

            val b = NotificationCompat.Builder(ctx, MIRROR_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(appLabel)
                .setStyle(big?.let { NotificationCompat.BigTextStyle().bigText(it) })
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(sbn.postTime)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            if (largeIconBmp != null) b.setLargeIcon(largeIconBmp)
            if (contentIntent != null) b.setContentIntent(contentIntent)

            // Forward action buttons from the original so the user
            // can archive / reply / mark-read directly from the
            // silent clone.
            src.actions?.forEach { action ->
                if (action == null || action.actionIntent == null) return@forEach
                val ic: IconCompat? = try {
                    action.getIcon()?.let { IconCompat.createFromIcon(ctx, it) }
                } catch (_: Exception) {
                    null
                }
                val compat = NotificationCompat.Action.Builder(ic, action.title, action.actionIntent).build()
                b.addAction(compat)
            }

            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.notify(mirrorId(pkg, sbn.key), b.build())
        } catch (e: Exception) {
            // Mirror is a nice-to-have; never propagate failures
            // up the listener thread, but log so a recurring issue
            // is at least visible in the rolling diagnostic.
            CrashLogger.logErr("NotificationMirror.repost failed", e)
        }
    }

    private fun resolveAppLabel(ctx: Context, pkg: String?): CharSequence = try {
        val pm = ctx.packageManager
        if (pkg == null) "" else pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        pkg.orEmpty()
    }

    private fun resolveAppIcon(ctx: Context, pkg: String?): Drawable? = try {
        if (pkg == null) null else ctx.packageManager.getApplicationIcon(pkg)
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        null
    }

    private fun launcherIntentFor(ctx: Context, pkg: String?): PendingIntent? = try {
        if (pkg == null) {
            null
        } else {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (i == null) {
                null
            } else {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                PendingIntent.getActivity(
                    ctx,
                    pkg.hashCode(),
                    i,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        }
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        null
    }

    private fun drawableToBitmap(d: Drawable?): Bitmap? {
        if (d == null) return null
        return try {
            if (d is BitmapDrawable) {
                d.bitmap?.let { return it }
            }
            var w = maxOf(1, d.intrinsicWidth)
            var h = maxOf(1, d.intrinsicHeight)
            // Clamp to a sane size — AdaptiveIconDrawable can report
            // bizarrely huge intrinsic dimensions on some OEMs.
            val max = 192
            if (w > max || h > max) {
                val s = max.toFloat() / maxOf(w, h)
                w = maxOf(1, (w * s).toInt())
                h = maxOf(1, (h * s).toInt())
            }
            val bmp = createBitmap(w, h)
            val c = Canvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(c)
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
