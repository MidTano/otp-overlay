// SPDX-License-Identifier: MIT
package com.midtano.otp.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Format an integer for display in the user's current locale. The
 * default [NumberFormat] renders Eastern Arabic / Persian / etc.
 * digits when the active locale calls for them and falls back to
 * ASCII for the en-US default.
 *
 * Using this extension everywhere keeps integer counters in
 * `TextView`s lint-clean (no `SetTextI18n` warnings) and lets us
 * change formatting in one place.
 */
fun Int.toLocaleString(): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(this.toLong())
