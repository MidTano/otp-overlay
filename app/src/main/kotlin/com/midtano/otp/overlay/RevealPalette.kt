// SPDX-License-Identifier: MIT
package com.midtano.otp.overlay

/**
 * Default brand-colour palette used by [OtpRevealLayout] for the
 * chromatic perimeter sweep, halo and countdown stroke.
 *
 * The hex literals are `0xAARRGGBB` because they are sweep-gradient
 * stops fed straight into [android.graphics.SweepGradient], not
 * surface colours, so the `colors.xml` indirection used elsewhere
 * does not apply here.
 */
internal object RevealPalette {

    /** Variations of the app-icon brand colour (deepest → palest). */
    const val CLR_ICON_DEEP: Int = 0xFF3A2FD9.toInt()
    const val CLR_ICON_DARK: Int = 0xFF5048E0.toInt()
    const val CLR_ICON_BASE: Int = 0xFF6C63FF.toInt()
    const val CLR_ICON_LIGHT: Int = 0xFF8C85FF.toInt()
    const val CLR_ICON_PALE: Int = 0xFFB8B3FF.toInt()

    /** Solid white countdown stroke, drawn on top of every other layer. */
    const val COUNTDOWN_WHITE: Int = 0xFFFFFFFF.toInt()

    /**
     * Default sweep-gradient stops. The first and last entries are
     * identical to seal the seam between 360° and 0° during rotation.
     * Returned freshly cloned so callers can mutate it without
     * disturbing the static template.
     */
    fun defaultSweepLoop(): IntArray = intArrayOf(
        CLR_ICON_DEEP,
        CLR_ICON_DARK,
        CLR_ICON_BASE,
        CLR_ICON_LIGHT,
        CLR_ICON_PALE,
        CLR_ICON_LIGHT,
        CLR_ICON_BASE,
        CLR_ICON_DARK,
        CLR_ICON_DEEP,
    )
}
