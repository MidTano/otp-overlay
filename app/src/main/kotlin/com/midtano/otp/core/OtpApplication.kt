// SPDX-License-Identifier: MIT
package com.midtano.otp.core

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.midtano.otp.system.CrashLogger

/**
 * Application entry point.
 *
 * Pins the AppCompat night mode to dark and installs [CrashLogger] so
 * any failure across any process component lands in the on-device
 * report file. Reading the report:
 *
 * ```
 *   adb shell run-as com.midtano.otp cat files/last_crash.txt
 *   adb shell run-as com.midtano.otp cat files/latest_log.txt
 * ```
 *
 * Or via Settings → Logs from inside the app.
 */
class OtpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply BEFORE installing the crash logger so an early
        // exception still surfaces against the right palette.
        //
        // Light theme is intentionally NOT supported. The overlay
        // surface is built around a dark glass card with chromatic
        // accents that depend on a deep-blue background to stay
        // legible (countdown halo, perimeter sweep, halftone wash).
        // A naive light variant would require a parallel res/values
        // sweep AND a re-tuned `RevealPalette` to keep contrast in
        // legal range — work that's out of scope for the current
        // milestone. Documented here so future contributors don't
        // assume it's an oversight.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        CrashLogger.install(this)
    }
}
