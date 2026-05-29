// SPDX-License-Identifier: MIT
package com.midtano.otp.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.midtano.otp.data.Prefs
import com.midtano.otp.service.overlay.OverlayClipboard
import com.midtano.otp.system.CrashLogger

/**
 * Pastes the OTP into a target [AccessibilityNodeInfo] via the
 * accessibility framework.
 *
 * Strategy:
 * 1. Try [AccessibilityNodeInfo.ACTION_SET_TEXT] first — it writes
 *    directly into the field without touching the clipboard.
 * 2. If `SET_TEXT` fails (some apps reject it for security-sensitive
 *    fields), put the OTP on the clipboard via [OverlayClipboard]
 *    and fire [AccessibilityNodeInfo.ACTION_PASTE]. The clipboard
 *    entry is marked sensitive on Android 13+ so it never appears
 *    in the system clipboard preview.
 * 3. If the user opted into "do not copy after auto-paste"
 *    ([Prefs.isAutopasteNoCopy]), clear the clipboard right after
 *    the paste.
 */
internal object AccessibilityPaster {

    fun pasteIntoNode(ctx: Context, node: AccessibilityNodeInfo?, otp: String): Boolean {
        if (node == null) return false

        // SET_TEXT first — does not touch the clipboard at all.
        var ok = false
        try {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    otp,
                )
            }
            ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            CrashLogger.logErr("paste: ACTION_SET_TEXT threw", e)
        }

        if (!ok) {
            // SET_TEXT failed — fall back to ACTION_PASTE which
            // requires the OTP to be on the clipboard.
            try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (cm != null) {
                    val clip = ClipData.newPlainText(OverlayClipboard.LABEL, otp)
                    OverlayClipboard.markSensitive(clip)
                    cm.setPrimaryClip(clip)
                }
            } catch (e: Exception) {
                CrashLogger.logErr("paste: clipboard prepare failed", e)
            }
            ok = try {
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (e: Exception) {
                CrashLogger.logErr("paste: ACTION_PASTE threw", e)
                false
            }
            if (Prefs.isAutopasteNoCopy(ctx)) {
                OverlayClipboard.clearPrimary(ctx)
            }
        }
        return ok
    }
}
