// SPDX-License-Identifier: MIT
package com.midtano.otp.core

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.LayoutInflaterCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.midtano.otp.data.Prefs
import com.midtano.otp.locale.AppLanguage
import com.midtano.otp.locale.LocaleHelper
import com.midtano.otp.locale.LocaleInflaterFactory
import com.midtano.otp.locale.LocaleSwitcher
import com.midtano.otp.system.CrashLogger

/**
 * Common Activity base.
 *
 * Responsibilities:
 * - Wraps the base context with the user's chosen locale via
 *   [LocaleHelper.wrap], so resources resolve correctly even on
 *   first launch before AppCompat picks the locale up itself.
 * - Installs [LocaleInflaterFactory] so every TextView remembers
 *   which `@string/` resource it was inflated from.
 * - Handles `onConfigurationChanged(locale)` by triggering a smooth
 *   swipe across all locale-tagged views via [LocaleSwitcher] — no
 *   flicker, no recreate.
 *
 * Activities declare `android:configChanges="locale|layoutDirection"`
 * in the manifest so Android delivers the change here instead of
 * destroying and recreating them.
 */
abstract class BaseActivity : AppCompatActivity() {

    /**
     * Direction hint for the next locale-change swipe, set by the
     * language switcher right before the locale flip. Defaults to
     * "swipe left" because the most common path (RU → EN) reads
     * left-to-right.
     */
    private var nextSwipeLeft: Boolean = true

    private var currentLang: AppLanguage? = null

    /**
     * Pre-arm the direction of the next locale-change swipe. Called
     * by `SettingsLanguageBinder` immediately before
     * [Prefs.setLanguage] flips the locale.
     */
    fun setPendingSwipeLeft(swipeLeft: Boolean) {
        this.nextSwipeLeft = swipeLeft
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the locale-aware inflater factory BEFORE
        // setContentView so every TextView inflated from layouts gets
        // tagged.
        val inflater = LayoutInflater.from(this)
        try {
            LayoutInflaterCompat.setFactory2(inflater, LocaleInflaterFactory(this))
        } catch (e: IllegalStateException) {
            // setFactory2 throws IllegalStateException when a factory
            // has already been installed by an underlying AppCompat
            // delegate. The activity stays usable; locale-tagged
            // swipe transitions just fall back to inflate-default.
            CrashLogger.logErr("LocaleInflaterFactory install failed", e)
        }
        super.onCreate(savedInstanceState)
        currentLang = Prefs.getLanguageTyped(this)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applySystemBarsInsets()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        applySystemBarsInsets()
    }

    override fun setContentView(view: View?, params: android.view.ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        applySystemBarsInsets()
    }

    /**
     * Pad the content view by the status-bar / navigation-bar
     * insets.
     *
     * `targetSdk 35` makes every activity edge-to-edge by default;
     * without this padding the top of every settings card and splash
     * heading would sit flush against the status bar, and long
     * scrolling content would disappear behind the navigation pill.
     *
     * Activities that want genuinely edge-to-edge content (the
     * splash with its full-bleed Lottie) override
     * [shouldApplySystemBarsInsets] to return `false`.
     */
    protected open fun applySystemBarsInsets() {
        if (!shouldApplySystemBarsInsets()) return
        val content = window?.decorView?.findViewById<View>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            // Consume the insets so child views do not double-pad.
            WindowInsetsCompat.CONSUMED
        }
        // Force an immediate dispatch so the very first frame has
        // the correct padding instead of briefly showing content
        // tucked under the status bar.
        ViewCompat.requestApplyInsets(content)
    }

    /**
     * Override and return `false` for activities that draw their
     * own backdrop edge-to-edge.
     */
    protected open fun shouldApplySystemBarsInsets(): Boolean = true

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newLang = Prefs.getLanguageTyped(this)
        if (newLang != currentLang) {
            // Push the new locale into both this activity's resources
            // and the application's resources so any subsequent
            // getString() lookup resolves to the right language.
            // Without this the activity stays frozen at the locale
            // we wrapped in attachBaseContext.
            LocaleHelper.applyToContext(this)
            LocaleHelper.applyToContext(applicationContext)

            getRootView()?.let { LocaleSwitcher.refresh(it, nextSwipeLeft) }
            updateTitleFromManifest()
            currentLang = newLang
        }
    }

    /** Helper for subclasses to fetch the content-view root. */
    protected fun getRootView(): View? =
        window?.decorView?.findViewById(android.R.id.content)

    /** Reload the activity's label from its manifest entry. */
    private fun updateTitleFromManifest() {
        try {
            val info = packageManager.getActivityInfo(componentName, 0)
            if (info.labelRes != 0) setTitle(info.labelRes)
        } catch (e: PackageManager.NameNotFoundException) {
            CrashLogger.logErr("updateTitleFromManifest: activity not found", e)
        }
    }
}
