// SPDX-License-Identifier: MIT
package com.midtano.otp.service.overlay

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Numeric constants and easing curves shared between
 * `OverlayService` and the controllers that orbit it. Pure
 * constants — no state, no Android Context.
 */
internal object OverlayServiceConfig {

    /**
     * Auto-copy timer. After this many milliseconds the OTP is
     * copied to the clipboard and the dismiss flourish kicks in.
     */
    const val AUTO_COPY_MS: Int = 10_000

    /**
     * Hard safety timeout. Even if every animator callback misfires,
     * the window must be torn down by this point.
     */
    const val WATCHDOG_MS: Long = (AUTO_COPY_MS + 3_000).toLong()

    /**
     * Grace period before reacting to `ACTION_SCREEN_OFF`. Phones
     * fire it liberally (proximity blank during a call, accidental
     * power-button taps, AOD transitions); a SCREEN_ON arriving
     * within this window means the event was spurious.
     */
    const val SCREEN_OFF_DEBOUNCE_MS: Long = 1_500L

    /** Failsafe teardown when the dismiss animator never fires its end callback. */
    const val SAFETY_DETACH_MS: Long = 1_500L

    /**
     * Hard upper bound on the copy-animation arc. Lottie up to ~8 s at
     * 0.5x speed + shrink 280 ms + dismiss 420 ms leaves headroom.
     */
    const val COPY_WATCHDOG_MS: Long = 11_000L

    /** Quick countdown retract while the card is dismissing. */
    const val COUNTDOWN_COLLAPSE_MS: Long = 180L

    /** Decelerating ease-out for short scale / fade animations. */
    val EASE_OUT: Interpolator = PathInterpolator(0.16f, 1f, 0.30f, 1f)

    /** Symmetric cubic-bezier for sheet expand / collapse. */
    val EASE_IN_OUT: Interpolator = PathInterpolator(0.65f, 0f, 0.35f, 1f)
}
