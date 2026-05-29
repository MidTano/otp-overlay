// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

/**
 * Reveal-effect intensity preset.
 *
 * The [storageId] is the on-disk integer that [PrefsFxLevel] persists.
 * Each entry pins its own [storageId] explicitly so re-ordering or
 * removing an entry stays compatible with already-saved values.
 *
 * - [LITE]  — default; full chromatic reveal with every layer enabled.
 * - [ULTRA] — minimal cost; drops the wave and halftone layers for
 *             low-end SoCs.
 */
enum class FxLevel(val storageId: Int) {
    LITE(0),
    ULTRA(1),
    ;

    companion object {
        /** Default applied when nothing has been stored yet. */
        val DEFAULT: FxLevel = LITE

        /**
         * Decode an integer from disk. Unknown values fall back to
         * [DEFAULT] so a corrupted preference file cannot crash the
         * FX picker.
         */
        fun fromStorageId(value: Int): FxLevel =
            entries.firstOrNull { it.storageId == value } ?: DEFAULT
    }
}
