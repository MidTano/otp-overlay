// SPDX-License-Identifier: MIT
package com.midtano.otp.system

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

/**
 * "Can the overlay be shown right now?" gate.
 *
 * Refuses to fire on lockscreen or sleep — the user cannot see the
 * card, and the system can lazily deliver SMS / notifications during
 * boot before SystemUI is ready, which would otherwise leak overlays
 * onto the lock surface.
 */
internal object ScreenState {

    fun isAvailable(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return pm.isInteractive && !km.isKeyguardLocked
    }
}
