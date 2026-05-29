 // SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.app.Activity
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import com.midtano.otp.widget.SpringSwitch

/**
 * Binds the simple toggles that gate the extraction pipeline:
 * skip foreground notifications, normalise non-Latin digits, and
 * the three-list filter switches (ignore / cleanup / currency)
 * with their dedicated phrase-list editors.
 */
internal object SettingsExtractionBinder {

    fun interface BoolSink {
        fun accept(value: Boolean)
    }

    fun bind(host: Activity) {
        bindToggle(
            host,
            R.id.switch_skip_foreground,
            R.id.tv_skip_fg_desc,
            R.string.desc_skip_fg_on,
            R.string.desc_skip_fg_off,
            Prefs.isSkipForeground(host),
        ) { Prefs.setSkipForeground(host, it) }

        bindToggle(
            host,
            R.id.switch_normalize_digits,
            R.id.tv_normalize_desc,
            R.string.desc_normalize_on,
            R.string.desc_normalize_off,
            Prefs.isNormalizeDigits(host),
        ) { Prefs.setNormalizeDigits(host, it) }

        bindIgnore(host)
        bindCleanup(host)
        bindCurrency(host)
    }

    private fun bindToggle(
        host: Activity,
        switchId: Int,
        descId: Int,
        onResId: Int,
        offResId: Int,
        initial: Boolean,
        sink: BoolSink,
    ) {
        val sw = host.findViewById<SpringSwitch?>(switchId) ?: return
        sw.setChecked(initial, false)
        SettingsDescriptionsBinder.applyAnimated(
            host,
            descId,
            initial,
            onResId,
            offResId,
            animate = false,
        )
        sw.setOnCheckedChangeListener { _, checked ->
            sink.accept(checked)
            SettingsDescriptionsBinder.applyAnimated(host, descId, checked, onResId, offResId)
        }
    }

    private fun bindIgnore(host: Activity) {
        val s = SettingsPhraseListBinder.Spec().apply {
            switchId = R.id.switch_ignore
            descId = R.id.tv_ignore_desc
            flowId = R.id.ignore_flow
            inputId = R.id.et_new_ignore
            addBtnId = R.id.btn_add_ignore
            resetBtnId = R.id.btn_reset_ignore
            onDescResId = R.string.desc_ignore_on
            offDescResId = R.string.desc_ignore_off
            emptyToastResId = R.string.toast_empty_word
            duplicateToastResId = R.string.toast_already_in_list
            resetToastResId = R.string.toast_reset
            getEnabled = SettingsPhraseListBinder.BoolReader { Prefs.isIgnoreEnabled(it) }
            setEnabled = SettingsPhraseListBinder.BoolWriter { ctx, v -> Prefs.setIgnoreEnabled(ctx, v) }
            getList = SettingsPhraseListBinder.Reader { Prefs.getIgnoredPhrases(it) }
            setList = SettingsPhraseListBinder.Writer { ctx, w -> Prefs.setIgnoredPhrases(ctx, w) }
            reset = SettingsPhraseListBinder.VoidAction { Prefs.resetIgnoredPhrases(it) }
        }
        SettingsPhraseListBinder.bind(host, s)
    }

    private fun bindCleanup(host: Activity) {
        val s = SettingsPhraseListBinder.Spec().apply {
            switchId = R.id.switch_cleanup
            descId = R.id.tv_cleanup_desc
            flowId = R.id.cleanup_flow
            inputId = R.id.et_new_cleanup
            addBtnId = R.id.btn_add_cleanup
            resetBtnId = R.id.btn_reset_cleanup
            onDescResId = R.string.desc_cleanup_on
            offDescResId = R.string.desc_cleanup_off
            emptyToastResId = R.string.toast_empty_word
            duplicateToastResId = R.string.toast_already_in_list
            resetToastResId = R.string.toast_reset
            getEnabled = SettingsPhraseListBinder.BoolReader { Prefs.isCleanupEnabled(it) }
            setEnabled = SettingsPhraseListBinder.BoolWriter { ctx, v -> Prefs.setCleanupEnabled(ctx, v) }
            getList = SettingsPhraseListBinder.Reader { Prefs.getCleanupPhrases(it) }
            setList = SettingsPhraseListBinder.Writer { ctx, w -> Prefs.setCleanupPhrases(ctx, w) }
            reset = SettingsPhraseListBinder.VoidAction { Prefs.resetCleanupPhrases(it) }
        }
        SettingsPhraseListBinder.bind(host, s)
    }

    private fun bindCurrency(host: Activity) {
        val s = SettingsPhraseListBinder.Spec().apply {
            switchId = R.id.switch_currency
            descId = R.id.tv_currency_desc
            flowId = R.id.currency_flow
            inputId = R.id.et_new_currency
            addBtnId = R.id.btn_add_currency
            resetBtnId = R.id.btn_reset_currency
            onDescResId = R.string.desc_currency_on
            offDescResId = R.string.desc_currency_off
            emptyToastResId = R.string.toast_empty_word
            duplicateToastResId = R.string.toast_already_in_list
            resetToastResId = R.string.toast_reset
            getEnabled = SettingsPhraseListBinder.BoolReader { Prefs.isCurrencySkipEnabled(it) }
            setEnabled = SettingsPhraseListBinder.BoolWriter { ctx, v -> Prefs.setCurrencySkipEnabled(ctx, v) }
            getList = SettingsPhraseListBinder.Reader { Prefs.getCurrencyTokens(it) }
            setList = SettingsPhraseListBinder.Writer { ctx, w -> Prefs.setCurrencyTokens(ctx, w) }
            reset = SettingsPhraseListBinder.VoidAction { Prefs.resetCurrencyTokens(it) }
        }
        SettingsPhraseListBinder.bind(host, s)
    }
}
