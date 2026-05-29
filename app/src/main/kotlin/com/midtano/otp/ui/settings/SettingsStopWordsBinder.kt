 // SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.app.Activity
import com.midtano.otp.R
import com.midtano.otp.data.Prefs

/**
 * Wires the stop-words editor in Settings: a master on/off switch,
 * an add box, a clear-all button, and a [com.midtano.otp.widget.FlowLayout]
 * of pill-shaped chips. When the switch is on, the OTP extractor
 * suppresses any notification whose text contains one of the
 * stored words.
 *
 * Implementation delegates to [SettingsPhraseListBinder]. New
 * entries are lower-cased via `Locale.ROOT` before storage so
 * matching stays case-insensitive.
 */
internal object SettingsStopWordsBinder {

    fun bind(host: Activity) {
        SettingsPhraseListBinder.bind(
            host,
            SettingsPhraseListBinder.Spec().apply {
                switchId = R.id.switch_stop_words
                descId = R.id.tv_stop_words_desc
                onDescResId = R.string.desc_stop_on
                offDescResId = R.string.desc_stop_off
                flowId = R.id.stop_words_flow
                inputId = R.id.et_new_stop
                addBtnId = R.id.btn_add_stop
                resetBtnId = R.id.btn_clear_stops
                emptyToastResId = R.string.toast_empty_word
                duplicateToastResId = R.string.toast_already_in_list
                resetToastResId = R.string.toast_stops_cleared
                lowercase = true
                getEnabled = SettingsPhraseListBinder.BoolReader { Prefs.isStopWordsEnabled(it) }
                setEnabled = SettingsPhraseListBinder.BoolWriter { ctx, v ->
                    Prefs.setStopWordsEnabled(ctx, v)
                }
                getList = SettingsPhraseListBinder.Reader { Prefs.getStopWords(it) }
                setList = SettingsPhraseListBinder.Writer { ctx, w -> Prefs.setStopWords(ctx, w) }
                reset = SettingsPhraseListBinder.VoidAction { Prefs.clearStopWords(it) }
            },
        )
    }
}
