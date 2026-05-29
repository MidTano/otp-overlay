// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.splash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.midtano.otp.BuildConfig
import com.midtano.otp.system.CrashLogger

/**
 * Builds the "crash report" text and pushes it to the system
 * clipboard so the user can paste it straight into a GitHub issue.
 *
 * Used by [SplashActivity] when the user taps "Report on GitHub"
 * after a crash.
 */
internal object CrashReportClipboard {

    /**
     * Best-effort copy. Silently no-ops if the clipboard service or
     * any of the [Build] / [CrashLogger] reads throw.
     */
    fun copy(ctx: Context) {
        try {
            val text = buildString {
                append("=== OTP Overlay Crash Report ===\n")
                append("App version: ").append(BuildConfig.VERSION_NAME)
                    .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                append("Build: ").append(BuildConfig.BUILD_ID)
                    .append(" · ").append(BuildConfig.BUILD_TIME).append('\n')
                append("Device: ").append(Build.MANUFACTURER).append(' ')
                    .append(Build.MODEL).append('\n')
                append("Android: ").append(Build.VERSION.RELEASE)
                    .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                append('\n')
                append(CrashLogger.readLastCrash(ctx))
            }
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("crash_report", text))
        } catch (e: Exception) {
            // Last-ditch path on a freshly-crashed process — the
            // crash flow must never throw a second time. Wide catch
            // is intentional; the user has already seen the crash
            // dialog and the report is silently dropped.
            CrashLogger.logErr("CrashReportClipboard.copy failed", e)
        }
    }
}
