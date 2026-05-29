// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.midtano.otp.system.CrashLogger

/**
 * Clipboard helpers shared by the overlay copy path and the
 * accessibility-paste fallback.
 *
 * On Android 13+ a clipboard entry can be tagged with
 * [ClipDescription.EXTRA_IS_SENSITIVE] so keyboards and the system
 * clipboard banner do not echo the digits back on screen — without
 * that flag, an auto-paste leaks the OTP onto the lock-screen
 * keyboard preview, defeating most of the privacy story.
 */
internal object OverlayClipboard {

    /**
     * Copy [otp] onto the system clipboard with the
     * sensitive-content flag set on supported OS versions. Failures
     * are logged but never thrown — clipboard exceptions are normal
     * mid-teardown and must not break the overlay flow.
     */
    fun copy(ctx: Context?, otp: String?) {
        if (ctx == null || otp.isNullOrEmpty()) return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        try {
            val clip = ClipData.newPlainText(LABEL, otp)
            markSensitive(clip)
            cm.setPrimaryClip(clip)
        } catch (e: SecurityException) {
            // ClipboardManager.setPrimaryClip throws SecurityException
            // when the foreground app contract is violated (e.g.
            // mid-teardown the service has lost the IME-bound
            // "active app" privilege).
            CrashLogger.logErr("OverlayClipboard.copy denied", e)
        } catch (e: IllegalStateException) {
            // Clipboard service can throw mid-teardown when its
            // remote handle has gone away.
            CrashLogger.logErr("OverlayClipboard.copy failed", e)
        }
    }

    /**
     * Clear the primary clipboard, swallowing exceptions. Used by
     * the auto-paste-no-copy flow once the field has been written.
     */
    fun clearPrimary(ctx: Context?) {
        if (ctx == null) return
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            cm.clearPrimaryClip()
        } catch (e: SecurityException) {
            CrashLogger.logErr("OverlayClipboard.clearPrimary denied", e)
        } catch (e: IllegalStateException) {
            CrashLogger.logErr("OverlayClipboard.clearPrimary failed", e)
        }
    }

    /**
     * Mark a [ClipData] as containing sensitive content. On API 33+
     * this stops keyboards / Pixel's clipboard banner from echoing
     * the OTP digits back on screen. No-op on older releases.
     */
    fun markSensitive(clip: ClipData) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val b = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
                clip.description.extras = b
            }
        } catch (e: IllegalArgumentException) {
            // PersistableBundle rejects values whose type isn't on
            // its allow-list — should never trip for a single
            // boolean, but keep the guard for parity with older
            // OEM ROMs that report a bogus type check.
            CrashLogger.logErr("OverlayClipboard.markSensitive failed", e)
        }
    }

    /** Standard clipboard label for OTP entries — surfaced by the system UI. */
    const val LABEL: String = "OTP"
}
