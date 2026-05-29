// SPDX-License-Identifier: MIT
package com.midtano.otp.locale

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.TextView
import com.midtano.otp.R
import com.midtano.otp.system.CrashLogger

/**
 * Walks a view tree and refreshes every [TextView] whose text or hint
 * was inflated from a `@string/` resource (tagged by
 * [LocaleInflaterFactory]) with the value from the current locale,
 * animated as a horizontal swipe.
 *
 * Direction:
 * - `swipeLeft = true`  → outgoing slides left, incoming enters from
 *   the right. Used for RU → EN transitions.
 * - `swipeLeft = false` → outgoing slides right, incoming enters from
 *   the left. Used for EN → RU.
 *
 * Total time ~400 ms: 180 ms exit, instant text swap, 220 ms enter.
 */
internal object LocaleSwitcher {

    private const val EXIT_MS = 180L
    private const val ENTER_MS = 220L

    private val EASE_OUT = PathInterpolator(0.16f, 1f, 0.30f, 1f)
    private val EASE_IN = PathInterpolator(0.45f, 0f, 0.55f, 1f)

    /** Refresh all locale-tagged text/hint values under [root]. */
    fun refresh(root: View?, swipeLeft: Boolean) {
        if (root == null) return
        val tracked = ArrayList<TextView>()
        collect(root, tracked)
        if (tracked.isEmpty()) return

        var distance = root.width.toFloat()
        if (distance <= 0f) distance = root.resources.displayMetrics.widthPixels.toFloat()
        val exitTx = if (swipeLeft) -distance * 0.18f else distance * 0.18f
        val enterFromTx = if (swipeLeft) distance * 0.18f else -distance * 0.18f

        for (v in tracked) {
            v.animate().cancel()
            v.animate()
                .translationX(exitTx)
                .alpha(0f)
                .setDuration(EXIT_MS)
                .setInterpolator(EASE_IN)
                .withEndAction {
                    applyNewText(v)
                    v.translationX = enterFromTx
                    v.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(ENTER_MS)
                        .setInterpolator(EASE_OUT)
                        .start()
                }
                .start()
        }
    }

    /**
     * Animate a swipe label change for a TextView whose new text is
     * supplied directly (used for the language picker button which
     * carries no static `@string/` tag — its label is "RU"/"EN").
     */
    fun swipeText(v: TextView?, newText: String, swipeLeft: Boolean) {
        if (v == null) return
        var distance = v.width.toFloat()
        if (distance <= 0f) distance = 80f * v.resources.displayMetrics.density
        val exitTx = if (swipeLeft) -distance * 0.6f else distance * 0.6f
        val enterFromTx = if (swipeLeft) distance * 0.6f else -distance * 0.6f

        v.animate().cancel()
        v.animate()
            .translationX(exitTx)
            .alpha(0f)
            .setDuration(EXIT_MS)
            .setInterpolator(EASE_IN)
            .withEndAction {
                v.text = newText
                v.translationX = enterFromTx
                v.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(ENTER_MS)
                    .setInterpolator(EASE_OUT)
                    .start()
            }
            .start()
    }

    /** Update text/hint of [v] from its tagged string resources. */
    private fun applyNewText(v: TextView) {
        val textTag = v.getTag(R.id.locale_text_res)
        val hintTag = v.getTag(R.id.locale_hint_res)
        // The view's own context is frozen at attachBaseContext time,
        // so v.setText(resId) would resolve against the OLD locale.
        // Build a context configured for the live locale and read
        // strings from there instead.
        val fresh = freshLocalisedContext(v.context)
        if (textTag is Int && textTag != 0) {
            runCatching { v.text = fresh.getString(textTag) }
        }
        if (hintTag is Int && hintTag != 0) {
            runCatching { v.hint = fresh.getString(hintTag) }
        }
    }

    private fun freshLocalisedContext(base: Context): Context = try {
        val locale = LocaleHelper.resolveLocale(base)
        val cfg = Configuration(base.resources.configuration).apply { setLocale(locale) }
        base.createConfigurationContext(cfg)
    } catch (e: IllegalStateException) {
        // Resources can be in a half-built state during configuration
        // changes; the unwrapped context still resolves the right
        // strings most of the time.
        CrashLogger.logErr("LocaleSwitcher.freshLocalisedContext failed", e)
        base
    }

    /** Recursively collect TextViews that carry locale tags. */
    private fun collect(root: View, out: MutableList<TextView>) {
        if (root is TextView) {
            val t = root.getTag(R.id.locale_text_res)
            val h = root.getTag(R.id.locale_hint_res)
            if (t is Int || h is Int) out.add(root)
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) collect(root.getChildAt(i), out)
        }
    }
}
