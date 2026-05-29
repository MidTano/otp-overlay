// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.midtano.otp.R
import com.midtano.otp.widget.FlowLayout
import com.midtano.otp.widget.SpringSwitch

/**
 * Reusable wiring for any "toggle + add + reset + chip flow"
 * editor in Settings. The hosting activity supplies a [Spec]
 * describing the view ids, the read / write hooks and the optional
 * reset hook.
 *
 * The binder short-circuits cleanly when [Spec.getList] /
 * [Spec.setList] are missing so callers can wire only the parts of
 * the editor they need without crashing on `!!` dereferences.
 */
internal object SettingsPhraseListBinder {

    fun interface Reader { fun read(host: Activity): List<String> }
    fun interface Writer { fun write(host: Activity, words: List<String>) }
    fun interface BoolReader { fun read(host: Activity): Boolean }
    fun interface BoolWriter { fun write(host: Activity, value: Boolean) }
    fun interface VoidAction { fun run(host: Activity) }

    /**
     * Configuration for one editor binding. Mutated by call sites
     * through `apply { ... }`; [bind] short-circuits if the
     * required `getList` / `setList` hooks are left null.
     */
    class Spec {
        var switchId: Int = 0
        var descId: Int = 0
        var flowId: Int = 0
        var inputId: Int = 0
        var addBtnId: Int = 0
        var resetBtnId: Int = 0
        var onDescResId: Int = 0
        var offDescResId: Int = 0
        var emptyToastResId: Int = 0
        var duplicateToastResId: Int = 0
        var resetToastResId: Int = 0

        /**
         * Lowercase trim every entry before storing. Used for
         * trigger / stop word lists where matching is case-insensitive
         * (`Locale.ROOT` to avoid Turkish I/ı folding pitfalls). Off
         * by default — the cleanup / ignore / currency lists keep
         * the user's original casing.
         */
        var lowercase: Boolean = false

        var getEnabled: BoolReader? = null
        var setEnabled: BoolWriter? = null
        var getList: Reader? = null
        var setList: Writer? = null
        var reset: VoidAction? = null
    }

    fun bind(host: Activity, s: Spec?) {
        if (s == null) return
        val getList = s.getList ?: return
        val setList = s.setList ?: return

        val sw = host.findViewById<SpringSwitch?>(s.switchId)
        val flow = host.findViewById<FlowLayout?>(s.flowId)
        val etNew = host.findViewById<EditText?>(s.inputId)
        val btnAdd = host.findViewById<Button?>(s.addBtnId)
        val btnReset = host.findViewById<Button?>(s.resetBtnId)
        if (flow == null || etNew == null || btnAdd == null) return

        val enabledReader = s.getEnabled
        val enabledWriter = s.setEnabled
        if (sw != null && enabledReader != null) {
            val initial = enabledReader.read(host)
            sw.setChecked(initial, false)
            applyDescription(host, s.descId, initial, s.onDescResId, s.offDescResId, animate = false)
            sw.setOnCheckedChangeListener { _, checked ->
                enabledWriter?.write(host, checked)
                applyDescription(host, s.descId, checked, s.onDescResId, s.offDescResId, animate = true)
            }
        }

        rebuild(host, flow, getList, setList)

        btnAdd.setOnClickListener {
            val raw = etNew.text?.toString().orEmpty().trim()
            val trimmed = if (s.lowercase) raw.lowercase(java.util.Locale.ROOT) else raw
            if (trimmed.isEmpty()) {
                Toast.makeText(host, host.getString(s.emptyToastResId), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val current = getList.read(host)
            if (current.contains(trimmed)) {
                Toast.makeText(host, host.getString(s.duplicateToastResId), Toast.LENGTH_SHORT).show()
                etNew.setText("")
                return@setOnClickListener
            }
            setList.write(host, current + trimmed)
            etNew.setText("")
            rebuild(host, flow, getList, setList)
        }

        val resetAction = s.reset
        if (btnReset != null && resetAction != null) {
            btnReset.setOnClickListener {
                resetAction.run(host)
                rebuild(host, flow, getList, setList)
                Toast.makeText(host, host.getString(s.resetToastResId), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyDescription(
        host: Activity,
        descId: Int,
        enabled: Boolean,
        onResId: Int,
        offResId: Int,
        animate: Boolean,
    ) {
        if (descId == 0 || onResId == 0 || offResId == 0) return
        SettingsDescriptionsBinder.applyAnimated(
            host,
            descId,
            enabled,
            onResId,
            offResId,
            animate,
        )
    }

    private fun rebuild(
        host: Activity,
        flow: FlowLayout,
        getList: Reader,
        setList: Writer,
    ) {
        flow.removeAllViews()
        val inflater = LayoutInflater.from(host)
        val words = getList.read(host)
        val margin = dp(host, 4f)
        for (word in words) {
            val chip = inflater.inflate(R.layout.item_trigger_chip, flow, false)
            chip.findViewById<TextView>(R.id.tv_chip_text).text = word
            chip.findViewById<ImageView>(R.id.iv_chip_delete).setOnClickListener {
                val cur = getList.read(host)
                setList.write(host, cur.filterNot { it == word })
                rebuild(host, flow, getList, setList)
            }
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                rightMargin = margin
                bottomMargin = margin
            }
            chip.layoutParams = lp
            flow.addView(chip)
        }
    }

    private fun dp(host: Activity, v: Float): Int =
        (v * host.resources.displayMetrics.density).toInt()
}
