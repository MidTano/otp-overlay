 // SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.app.Activity
import com.midtano.otp.R
import com.midtano.otp.data.Prefs

/**
 * Wires the trigger-word editor in Settings: a search box plus
 * add button, a reset-to-defaults button, and a [com.midtano.otp.widget.FlowLayout]
 * of pill-shaped chips, one per persisted keyword.
 *
 * Implementation delegates to [SettingsPhraseListBinder] — the
 * generic chip-flow infrastructure the cleanup, ignore, currency
 * and stop-word editors all share. New entries are lower-cased
 * via `Locale.ROOT` before storage so trigger matching stays
 * case-insensitive without the Turkish I/? folding hazard.
 *
 * State lives in [Prefs.getTriggerWords] / [Prefs.setTriggerWords].
 */
internal object SettingsTriggerWordsBinder {

    fun bind(host: Activity) {
        SettingsPhraseListBinder.bind(
            host,
            SettingsPhraseListBinder.Spec().apply {
                flowId = R.id.triggers_flow
                inputId = R.id.et_new_trigger
                addBtnId = R.id.btn_add_trigger
                resetBtnId = R.id.btn_reset_triggers
                emptyToastResId = R.string.toast_empty_word
                duplicateToastResId = R.string.toast_already_in_list
                resetToastResId = R.string.toast_reset
                lowercase = true
                getList = SettingsPhraseListBinder.Reader { Prefs.getTriggerWords(it) }
                setList = SettingsPhraseListBinder.Writer { ctx, w -> Prefs.setTriggerWords(ctx, w) }
                reset = SettingsPhraseListBinder.VoidAction { Prefs.resetTriggerWords(it) }
            },
        )
    }
}
