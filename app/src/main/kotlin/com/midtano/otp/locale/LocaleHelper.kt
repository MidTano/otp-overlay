// SPDX-License-Identifier: MIT
package com.midtano.otp.locale

import android.content.Context
import android.content.res.Configuration
import com.midtano.otp.data.prefs.PrefsLocale
import com.midtano.otp.system.CrashLogger
import java.util.Locale

/**
 * Wraps a [Context] with the user-selected locale so every Activity,
 * Service and Application component renders strings in the right
 * language.
 *
 * Usage in every Activity / Service:
 * ```
 *   override fun attachBaseContext(base: Context) {
 *       super.attachBaseContext(LocaleHelper.wrap(base))
 *   }
 * ```
 *
 * Lint flags this class with `AppBundleLocaleChanges` because we
 * change locale at runtime without using Play Core to download
 * language splits. The `bundle.language.enableSplit = false` setting
 * in `app/build.gradle.kts` disables per-language splits so every
 * supported locale ships inside the base APK and runtime switching
 * works without a Play Asset Delivery hop.
 */
internal object LocaleHelper {

    /**
     * Resolve the user's preferred locale.
     *
     * Routed through the typed [PrefsLocale.getLanguageTyped] so an
     * unknown stored value lands on [AppLanguage.DEFAULT] (English)
     * rather than silently flipping the UI to Russian, which was
     * the previous bug.
     */
    fun resolveLocale(ctx: Context): Locale = PrefsLocale.getLanguageTyped(ctx).locale

    /**
     * Return a context whose resources are configured for the user's
     * chosen locale. Uses `createConfigurationContext`, which is the
     * non-deprecated API since API 17.
     */
    fun wrap(base: Context): Context {
        val locale = resolveLocale(base)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /**
     * Apply the user's locale to an existing context in place. Used
     * when a component (typically a Service) cannot recreate itself
     * with a wrapped context but still needs subsequent `getString()`
     * calls to resolve to the right language.
     *
     * The deprecated `Resources.updateConfiguration` is the only path
     * that mutates an already-attached context's resources. Failures
     * are logged through [CrashLogger] rather than swallowed.
     */
    @Suppress("DEPRECATION")
    fun applyToContext(ctx: Context?) {
        if (ctx == null) return
        try {
            val locale = resolveLocale(ctx)
            Locale.setDefault(locale)
            val config = Configuration(ctx.resources.configuration).apply { setLocale(locale) }
            ctx.resources.updateConfiguration(config, ctx.resources.displayMetrics)
        } catch (e: IllegalStateException) {
            // updateConfiguration is deprecated and on some OEM
            // ROMs guards itself with an IllegalStateException when
            // called outside the framework's expected lifecycle
            // (e.g. from a Service that's about to die). Falling
            // back leaves the previous locale in effect — better
            // than crashing the listener.
            CrashLogger.logErr("LocaleHelper.applyToContext failed", e)
        }
    }
}
