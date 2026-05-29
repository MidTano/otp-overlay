// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

import com.midtano.otp.R

/**
 * Shared Lottie animation pool used by both the copy-celebration
 * burst ([CopyLottiePlayer]) and the auto-paste confirmation pill
 * ([com.midtano.otp.service.overlay.AutoPasteToastController]).
 *
 * One source of truth so adding or removing an emoji means editing
 * a single list.
 */
internal object CopyLottiePool {
    /** Lottie resource ids; one is picked at random per show. */
    val RES: IntArray = intArrayOf(
        R.raw.lottie_emoji_wink,
        R.raw.lottie_emoji_shush,
        R.raw.lottie_emoji_upside_down,
        R.raw.lottie_emoji_smart,
        R.raw.lottie_emoji_eyes_roll,
        R.raw.lottie_emoji_wave,
        R.raw.lottie_emoji_celebrate,
        R.raw.lottie_emoji_like,
        R.raw.lottie_emoji_unamused,
    )
}
