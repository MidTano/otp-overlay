// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.about

import android.app.Activity
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.airbnb.lottie.LottieAnimationView
import com.midtano.otp.BuildConfig
import com.midtano.otp.R

/**
 * Easter-egg "About this build" dialog reachable from the build-info
 * footer on [com.midtano.otp.ui.main.MainActivity].
 *
 * The dialog is intentionally never advertised — `tv_build_info`
 * carries no ripple, no underline, no `?attr/selectableItemBackground`
 * in the layout. A user who taps the technical-looking version line
 * gets a small card with everything they need to follow the project:
 * version / build hash / repository link / latest release / issues /
 * how to contribute / contributors.
 *
 * The app makes no network calls itself — every row routes through
 * [AboutLinks.openUrl] (i.e. [android.content.Intent.ACTION_VIEW]),
 * so the dialog is permission-free and will keep working in fully
 * offline regions once the browser comes back online.
 *
 * Adding a new row is a [R.layout.dialog_about] edit + one
 * [AboutLinks.bindRow] entry; this file deliberately stays small.
 */
internal object AboutDialog {

    /** Show the about dialog, anchored to [host]. Safe to call repeatedly. */
    fun show(host: Activity) {
        val view = host.layoutInflater.inflate(R.layout.dialog_about, null, false)

        view.findViewById<TextView?>(R.id.tv_about_version)?.text = host.getString(
            R.string.about_dialog_version, BuildConfig.VERSION_NAME,
        )
        view.findViewById<TextView?>(R.id.tv_about_build)?.text = host.getString(
            R.string.about_dialog_build, BuildConfig.BUILD_ID, BuildConfig.BUILD_TIME,
        )

        AboutLinks.tintWhite(view.findViewById<LottieAnimationView?>(R.id.lottie_about_header))

        val dialog = AlertDialog.Builder(host).setView(view).create()
        // Drop the default white window background so the rounded
        // dark @drawable/about_dialog_bg shows through cleanly.
        dialog.window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setBackgroundDrawableResource(R.drawable.about_dialog_bg)
        }

        AboutLinks.bindRow(view, R.id.about_row_repo, R.string.about_repo_url, host, dialog)
        AboutLinks.bindRow(view, R.id.about_row_release, R.string.about_release_url, host, dialog)
        AboutLinks.bindRow(view, R.id.about_row_issues, R.string.about_issues_url, host, dialog)
        AboutLinks.bindRow(view, R.id.about_row_contribute, R.string.about_contribute_url, host, dialog)
        AboutLinks.bindRow(view, R.id.about_row_contributors, R.string.about_contributors_url, host, dialog)

        view.findViewById<Button?>(R.id.about_btn_close)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
