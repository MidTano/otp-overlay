// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.edit

/**
 * Reveal-effect intensity preset and the device-class auto-pick that
 * drives its default value.
 *
 * The storage key sits separately from the per-knob FX values so a
 * user who never opens the FX tuner still receives a sensible default
 * derived from [recommendedLevel]. The on-disk format is the
 * [FxLevel.storageId] (ordinal).
 */
object PrefsFxLevel {

    internal const val KEY_FX_LEVEL = "fx_level"

    /** Resolve the persisted FX level, falling back to [recommendedLevel]. */
    fun getLevel(c: Context): FxLevel {
        val defaultId = recommendedLevel(c).storageId
        val stored = PrefsFile.sp(c).getInt(KEY_FX_LEVEL, defaultId)
        return FxLevel.fromStorageId(stored)
    }

    fun setLevel(c: Context, level: FxLevel) {
        PrefsFile.sp(c).edit { putInt(KEY_FX_LEVEL, level.storageId) }
    }

    /**
     * Device-class FX recommendation. Surfaced in Settings so the
     * user can see why a given level was picked for them.
     *
     * Heuristic — kept simple to survive OEM `Build.*` lies and noisy
     * [ActivityManager.MemoryInfo]:
     * - [ActivityManager.isLowRamDevice] → ULTRA
     * - total RAM < ~3 GB → ULTRA
     * - otherwise → LITE
     *
     * Returns [FxLevel.DEFAULT] on any unexpected failure.
     */
    fun recommendedLevel(c: Context): FxLevel {
        return try {
            val am = c.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)
                as? ActivityManager ?: return FxLevel.DEFAULT
            if (am.isLowRamDevice) return FxLevel.ULTRA
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val ramMb = mi.totalMem / (1024L * 1024L)
            if (ramMb < 3072) FxLevel.ULTRA else FxLevel.LITE
        } catch (_: Exception) {
            FxLevel.DEFAULT
        }
    }
}
