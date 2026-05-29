// SPDX-License-Identifier: MIT
package com.midtano.otp.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that [DisplayMode.fromStorageId] and [FxLevel.fromStorageId]
 * clamp out-of-range integers instead of throwing. The constraints
 * matter because the storage value can be corrupted by an out-of-band
 * `adb shell` write or an old build whose enum had fewer entries.
 */
class EnumStorageTest {

    @Test
    fun displayModeRoundTrip() {
        for (mode in DisplayMode.entries) {
            assertEquals(mode, DisplayMode.fromStorageId(mode.storageId))
        }
    }

    @Test
    fun displayModeNegativeClampsToFirst() {
        assertEquals(DisplayMode.entries.first(), DisplayMode.fromStorageId(-1))
        assertEquals(DisplayMode.entries.first(), DisplayMode.fromStorageId(Int.MIN_VALUE))
    }

    @Test
    fun displayModeOverflowClampsToLast() {
        assertEquals(DisplayMode.entries.last(), DisplayMode.fromStorageId(99))
        assertEquals(DisplayMode.entries.last(), DisplayMode.fromStorageId(Int.MAX_VALUE))
    }

    @Test
    fun fxLevelRoundTrip() {
        for (level in FxLevel.entries) {
            assertEquals(level, FxLevel.fromStorageId(level.storageId))
        }
    }

    @Test
    fun fxLevelClampsOutOfRange() {
        // Unknown / corrupted on-disk values fall back to DEFAULT
        // (LITE) instead of throwing so the FX picker stays usable
        // after a downgrade or a stray adb-shell write.
        assertEquals(FxLevel.DEFAULT, FxLevel.fromStorageId(-5))
        assertEquals(FxLevel.DEFAULT, FxLevel.fromStorageId(100))
    }

    @Test
    fun fxLevelLegacyFullFoldsToLite() {
        // The retired "FULL" preset used storageId 2; old installs
        // that picked it before the removal must keep a sensible
        // visual default.
        assertEquals(FxLevel.LITE, FxLevel.fromStorageId(2))
    }

    @Test
    fun fxLevelStorageIdsArePinned() {
        // Pinned storage ids (LITE = 0, ULTRA = 1). Locking the
        // contract in here so reordering or renaming entries does
        // not silently shift the on-disk layout.
        assertEquals(0, FxLevel.LITE.storageId)
        assertEquals(1, FxLevel.ULTRA.storageId)
    }
}
