// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** Haptic feedback for the overlay (single 60 ms confirmation buzz). */
internal object OverlayHaptics {

    /** Play the short tactile confirmation. No-op on devices without a vibrator. */
    fun vibrateLight(ctx: Context) {
        val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        val vib = vm?.defaultVibrator ?: return
        if (!vib.hasVibrator()) return
        vib.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
