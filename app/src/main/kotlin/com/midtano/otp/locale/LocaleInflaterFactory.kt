// SPDX-License-Identifier: MIT
package com.midtano.otp.locale

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.midtano.otp.R

/**
 * [LayoutInflater.Factory2] that records the `@string/` resource each
 * TextView's `android:text` and `android:hint` were inflated with.
 * The IDs land in view tags ([R.id.locale_text_res],
 * [R.id.locale_hint_res]) so [LocaleSwitcher] can re-apply them when
 * the user changes language without recreating the activity.
 *
 * View creation is delegated to AppCompat's [AppCompatDelegate] so
 * widget upgrades (TextView → AppCompatTextView, etc.) still happen.
 */
internal class LocaleInflaterFactory(activity: AppCompatActivity) : LayoutInflater.Factory2 {

    private val delegate: AppCompatDelegate = activity.delegate

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet,
    ): View? {
        val view = delegate.createView(parent, name, context, attrs)
        if (view is TextView) {
            val textRes = attrs.getAttributeResourceValue(ANDROID_NS, "text", 0)
            val hintRes = attrs.getAttributeResourceValue(ANDROID_NS, "hint", 0)
            if (textRes != 0) view.setTag(R.id.locale_text_res, textRes)
            if (hintRes != 0) view.setTag(R.id.locale_hint_res, hintRes)
        }
        return view
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
        onCreateView(null, name, context, attrs)

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
