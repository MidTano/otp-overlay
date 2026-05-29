// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Single source of truth for the on-disk [SharedPreferences] file used
 * by every `Prefs*` helper in this package.
 *
 * Internal — call sites should reach for [com.midtano.otp.data.Prefs]
 * or one of the typed helpers below it.
 */
internal object PrefsFile {

    /** Backing SharedPreferences filename. */
    const val NAME: String = "otp_prefs"

    /** Application-scoped accessor for the shared store. */
    fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Inclusive integer clamp used by every numeric setter. */
    fun clampI(v: Int, lo: Int, hi: Int): Int = when {
        v < lo -> lo
        v > hi -> hi
        else -> v
    }
}
