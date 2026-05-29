// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

/**
 * Single source of truth for the per-app row in the Settings →
 * "Apps filter" list.
 *
 * Used by [SettingsAppListBinder] (load + filter), [AppsAdapter]
 * (render + diff), and [SettingsActivity] (held as a `lateinit`
 * field so the binder can hand the same instance back to the
 * adapter without an intermediate copy).
 *
 * Kept as a `data class` because `equals` powers `DiffUtil.calculateDiff`.
 *
 * - [pkg] is the canonical Android package name. Never null on a
 *   row that survives [SettingsAppListBinder.load]; the nullable
 *   declaration is only there because adapters reuse the type for
 *   "loading…" placeholders.
 * - [name] is the user-facing label resolved through the package
 *   manager.
 * - [nameLower] is `name.lowercase(Locale.ROOT)` cached for the
 *   case-insensitive search filter; the binder writes it once at
 *   load time so the filter doesn't have to lower-case 200+
 *   labels per keystroke.
 */
internal data class AppInfo(
    var name: String? = null,
    var nameLower: String? = null,
    var pkg: String? = null,
)
