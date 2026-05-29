// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.splash

/**
 * Timing schedule for the cold-launch greeting / onboarding intro
 * Lottie animation, derived from the loaded composition's duration.
 *
 * The rest of the choreography is layered on top of a single Lottie:
 *
 * ```
 * 0%                          100%
 * │                              │
 * ├── lottie plays (full length) ┤
 * │   ├ text fade-in (delay)     │
 * │   │                          │
 * │            ┌─ collapse start ┘
 * │            └─ suck-in start  ┘
 * ```
 *
 * Every timing is a *fraction of the Lottie composition*, so a
 * shorter or longer Lottie keeps the same visual rhythm — the
 * suck-in still lands right before the Lottie ends, the text still
 * appears in the same relative beat.
 *
 * Fractions are clamped to absolute min/max values so a degenerate
 * 200 ms Lottie does not produce a 28 ms collapse animation that
 * the user would never see.
 *
 * The reference Lottie set in this app is 3 000 ms / 60 fps; with
 * that length the schedule expands to:
 *
 * - text appears  at  16.7 % (500 ms)
 * - text fade     for 16.7 % (500 ms)
 * - suck-in start at  86.7 % (2 600 ms)
 * - suck-in lasts for  9.3 % (280 ms)
 * - collapse start at 86.0 % (2 580 ms)
 * - collapse lasts for 14 % (420 ms)
 * - fallback timeout at 200 % (6 000 ms)
 */
data class SplashAnimTimings(
    val textAppearDelayMs: Long,
    val textFadeDurationMs: Long,
    val suckInStartMs: Long,
    val suckInDurationMs: Long,
    val collapseStartMs: Long,
    val collapseDurationMs: Long,
    val fallbackMs: Long,
) {
    companion object {
        /** Text appears at [TEXT_APPEAR_FRACTION] of the Lottie. */
        private const val TEXT_APPEAR_FRACTION = 0.167

        /** Text fade-in lasts [TEXT_FADE_FRACTION] of the Lottie. */
        private const val TEXT_FADE_FRACTION = 0.167

        /**
         * Suck-in begins this far before the Lottie's end, so the
         * dark background is collapsing into the centre while the
         * Lottie itself is still finishing.
         */
        private const val SUCK_IN_LEAD_FRACTION = 0.133

        /** Length of the suck-in, as fraction of the Lottie. */
        private const val SUCK_IN_DURATION_FRACTION = 0.093

        /** Lottie scale-down begins this far before the Lottie's end. */
        private const val COLLAPSE_LEAD_FRACTION = 0.14

        /** Length of the Lottie scale-collapse, as fraction of the Lottie. */
        private const val COLLAPSE_DURATION_FRACTION = 0.14

        /**
         * Hard upper bound for the splash overlay before we hide it
         * unconditionally. Set to twice the Lottie length so a
         * Lottie that never fires `onAnimationEnd` (rare; observed
         * on heavily throttled GPUs) still recovers.
         */
        private const val FALLBACK_FACTOR = 2.0

        // Floors / ceilings for absolute durations. Without these a
        // very short Lottie would produce sub-100 ms effects that
        // are impossible to perceive, and a very long Lottie would
        // produce a multi-second suck-in that drags the experience.
        private const val MIN_TEXT_FADE_MS = 280L
        private const val MAX_TEXT_FADE_MS = 800L
        private const val MIN_SUCK_DUR_MS = 200L
        private const val MAX_SUCK_DUR_MS = 600L
        private const val MIN_COLLAPSE_DUR_MS = 240L
        private const val MAX_COLLAPSE_DUR_MS = 700L
        private const val MIN_FALLBACK_MS = 4_000L

        /**
         * Build a timing schedule for a Lottie of the given total
         * duration. Returns sensible defaults if [totalLottieMs] is
         * non-positive (e.g. composition not loaded yet).
         */
        fun forDuration(totalLottieMs: Long): SplashAnimTimings {
            // Defensive fallback for an unknown composition: assume
            // the 3 000 ms reference length.
            val total = if (totalLottieMs > 0L) totalLottieMs else 3_000L

            val textAppear = (total * TEXT_APPEAR_FRACTION).toLong().coerceAtLeast(0L)
            val textFade = (total * TEXT_FADE_FRACTION).toLong()
                .coerceIn(MIN_TEXT_FADE_MS, MAX_TEXT_FADE_MS)

            val suckLead = (total * SUCK_IN_LEAD_FRACTION).toLong()
            val suckStart = (total - suckLead).coerceAtLeast(0L)
            val suckDuration = (total * SUCK_IN_DURATION_FRACTION).toLong()
                .coerceIn(MIN_SUCK_DUR_MS, MAX_SUCK_DUR_MS)

            val collapseLead = (total * COLLAPSE_LEAD_FRACTION).toLong()
            val collapseStart = (total - collapseLead).coerceAtLeast(0L)
            val collapseDuration = (total * COLLAPSE_DURATION_FRACTION).toLong()
                .coerceIn(MIN_COLLAPSE_DUR_MS, MAX_COLLAPSE_DUR_MS)

            val fallback = (total * FALLBACK_FACTOR).toLong()
                .coerceAtLeast(MIN_FALLBACK_MS)

            return SplashAnimTimings(
                textAppearDelayMs = textAppear,
                textFadeDurationMs = textFade,
                suckInStartMs = suckStart,
                suckInDurationMs = suckDuration,
                collapseStartMs = collapseStart,
                collapseDurationMs = collapseDuration,
                fallbackMs = fallback,
            )
        }
    }
}
