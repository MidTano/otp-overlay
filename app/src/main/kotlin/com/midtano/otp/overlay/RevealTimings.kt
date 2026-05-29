// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

/**
 * Animation timing constants for [OtpRevealLayout].
 *
 * Durations are wall-clock milliseconds. Crossfade points are
 * fractions of [REVEAL_TOTAL_MS] (0..1) — anim-progress at which a
 * given channel starts (`*_IN`) and reaches full opacity (`*_OUT`).
 *
 * Constants live here rather than as instance fields because they
 * are shared across every reveal cycle and are not driven by
 * [FxKnobs] (those are runtime multipliers applied on top).
 */
internal object RevealTimings {

    /** Default total reveal length, split between channels below. */
    const val REVEAL_TOTAL_MS: Long = 1100L

    /** Single-shot dismiss fade. */
    const val DISMISS_DURATION_MS: Long = 240L

    /** Pulse on copy hit (badge ring expansion). */
    const val COPY_PULSE_MS: Long = 320L

    /** Lottie burst overlay for the copy confirmation. */
    const val COPY_BURST_MS: Long = 760L

    /** Halo radial gradient — earliest channel; sets the substrate. */
    const val HALO_IN: Float = 0.00f
    const val HALO_OUT: Float = 0.85f

    /** Spark perimeter — comes in just after halo. */
    const val SPARK_IN: Float = 0.02f
    const val SPARK_OUT: Float = 0.55f

    /** Wave channel — mid-cycle accent. */
    const val WAVE_IN: Float = 0.10f
    const val WAVE_OUT: Float = 0.90f

    /** Halftone dot grid — late channel. */
    const val HALFTONE_IN: Float = 0.18f
    const val HALFTONE_OUT: Float = 1.00f

    /** Card alpha starts crossing in here so it lands after the wash. */
    const val CARD_IN: Float = 0.30f

    /** Perimeter chromatic glow — joins last so it reads as a "settle". */
    const val PERIMETER_IN: Float = 0.65f
}
