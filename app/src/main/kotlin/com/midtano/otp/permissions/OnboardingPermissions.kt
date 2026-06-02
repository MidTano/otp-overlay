 // SPDX-License-Identifier: MIT
package com.midtano.otp.permissions

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.midtano.otp.service.OtpAccessibilityService

/**
 * Permission-state queries and "open the right system page"
 * launchers used by the onboarding flow.
 */
internal object OnboardingPermissions {

    /** Request codes consumed in `onRequestPermissionsResult`. */
    const val REQ_SMS: Int = 41
    const val REQ_POST_NOTIF: Int = 42

    fun isOverlayGranted(ctx: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
        } else {
            true
        }
    }

    fun isSmsGranted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun isNotificationListenerEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(
            ctx.contentResolver,
            "enabled_notification_listeners",
        )
        return !flat.isNullOrEmpty() && flat.contains(ctx.packageName)
    }

    /**
     * `true` iff this app's accessibility service is in
     * [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES]. Compares the
     * [ComponentName] explicitly so an unrelated service does not
     * falsely register.
     */
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        if (enabled.isNullOrEmpty()) return false
        val want = ComponentName(ctx, OtpAccessibilityService::class.java)
        val wantFlat = want.flattenToString()
        val wantShort = want.flattenToShortString()
        for (entry in enabled.split(":")) {
            if (entry.isEmpty()) continue
            if (entry.equals(wantFlat, ignoreCase = true) ||
                entry.equals(wantShort, ignoreCase = true)
            ) {
                return true
            }
            val parsed = ComponentName.unflattenFromString(entry) ?: continue
            if (parsed == want) return true
        }
        return false
    }

    fun requestOverlay(host: Activity) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
        if (Settings.canDrawOverlays(host)) return
        host.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${host.packageName}".toUri(),
            ),
        )
    }

    /**
     * Request [Manifest.permission.RECEIVE_SMS]. The hosting activity
     * follows up with `POST_NOTIFICATIONS` on Android 13+ from its
     * own `onRequestPermissionsResult`.
     *
     * The receiver-only flow does not need `READ_SMS`, which gates
     * `content://sms` queries we never run; the platform delivers
     * SMS bodies through the broadcast itself.
     */
    fun requestSms(host: Activity) {
        if (isSmsGranted(host)) return
        ActivityCompat.requestPermissions(
            host,
            arrayOf(Manifest.permission.RECEIVE_SMS),
            REQ_SMS,
        )
    }

    fun requestNotifAccess(host: Activity) {
        if (isNotificationListenerEnabled(host)) return
        try {
            host.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            host.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun requestAccessibility(host: Activity) {
        if (isAccessibilityEnabled(host)) return
        host.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
