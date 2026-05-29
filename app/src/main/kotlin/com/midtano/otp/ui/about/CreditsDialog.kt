// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.about

import android.app.Activity
import android.view.Window
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.airbnb.lottie.LottieAnimationView
import com.midtano.otp.R

/**
 * Easter-egg "Credits" dialog reachable from the small footer note
 * under the build-info line on [com.midtano.otp.ui.main.MainActivity].
 *
 * Acknowledges the third-party Telegram emoji pack
 * (`KawaiiEmoji`) used as the visual source for every Lottie
 * animation in the app, and gives the original author a way to
 * reach out (a GitHub issue link) if they object to the use.
 *
 * Same lightbox shape as [AboutDialog]: a 96-dp white-tinted Lottie
 * header, an explanatory paragraph, two tappable rows and a flat
 * "Close" CTA. Both dialogs share row-binding plumbing through
 * [AboutLinks], so neither file grows past ~70 lines.
 *
 * No network calls — every link routes through
 * [AboutLinks.openUrl].
 */
internal object CreditsDialog {

    /** Show the credits dialog, anchored to [host]. Safe to call repeatedly. */
    fun show(host: Activity) {
        val view = host.layoutInflater.inflate(R.layout.dialog_credits, null, false)

        AboutLinks.tintWhite(view.findViewById<LottieAnimationView?>(R.id.lottie_credits_header))

        val dialog = AlertDialog.Builder(host).setView(view).create()
        dialog.window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawableResource(R.drawable.about_dialog_bg)
        }

        AboutLinks.bindRow(view, R.id.credits_row_pack, R.string.credits_emoji_pack_url, host, dialog)
        AboutLinks.bindRow(view, R.id.credits_row_contact, R.string.about_issues_url, host, dialog)

        view.findViewById<Button?>(R.id.credits_btn_close)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
