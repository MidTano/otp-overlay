// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.about

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.midtano.otp.R
import com.midtano.otp.system.CrashLogger

/**
 * Shared plumbing for the easter-egg About / Credits dialogs.
 *
 * Both dialogs are tiny lightboxes that render a Lottie header,
 * some text and a small list of "tappable" rows wired to
 * [Intent.ACTION_VIEW]. Centralising the row binding and the
 * Lottie recolour here keeps each dialog file under 100 lines and
 * makes it trivial to add a third dialog of the same shape later.
 */
internal object AboutLinks {

    /**
     * Recolour every layer of [lottie] to a flat white once the
     * composition has loaded. Mirrors the Splash / Onboarding /
     * Main Lottie treatment so every animation in the app shares
     * one visual language on the dark background.
     */
    fun tintWhite(lottie: LottieAnimationView?) {
        if (lottie == null) return
        lottie.addLottieOnCompositionLoadedListener {
            lottie.addValueCallback(
                KeyPath("**"),
                LottieProperty.COLOR,
                LottieValueCallback(Color.WHITE),
            )
            lottie.addValueCallback(
                KeyPath("**"),
                LottieProperty.STROKE_COLOR,
                LottieValueCallback(Color.WHITE),
            )
        }
    }

    /**
     * Wire one tappable row to its URL. `urlResId` resolves to a
     * `translatable="false"` string in `strings.xml`, so the link
     * never picks up locale-specific tampering and stays auditable
     * with a single grep.
     *
     * Tapping the row dismisses [dialog] (so the user doesn't
     * return to a dim card stuck behind the browser), then opens
     * the URL in an external browser via [Intent.ACTION_VIEW].
     */
    fun bindRow(
        root: View,
        rowId: Int,
        urlResId: Int,
        host: Activity,
        dialog: AlertDialog,
    ) {
        val row = root.findViewById<View?>(rowId) ?: return
        row.setOnClickListener {
            openUrl(host, host.getString(urlResId))
            dialog.dismiss()
        }
    }

    /**
     * Open [url] in the user's browser. Catches the no-browser
     * case and surfaces a localised toast — the dialogs that call
     * this are non-essential, so we never propagate the failure.
     */
    fun openUrl(host: Activity, url: String) {
        try {
            host.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                host,
                host.getString(R.string.about_browser_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
            CrashLogger.logErr("AboutLinks: no browser to open $url", e)
        }
    }
}
