// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.stats

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.midtano.otp.R
import com.midtano.otp.core.BaseActivity
import com.midtano.otp.extractor.OtpSource
import com.midtano.otp.extractor.OtpStats
import com.midtano.otp.util.toLocaleString
import com.midtano.otp.widget.BarChartView
import java.util.Calendar

/**
 * Stats dashboard.
 *
 * Reads from [OtpStats] and renders five sections:
 * 1. headline counters (total / today),
 * 2. 7-day bar chart of received OTP volume,
 * 3. top-senders list with app icons and per-sender counts,
 * 4. source split (SMS / push / test),
 * 5. last crash report and rolling log (linked, not embedded).
 *
 * No network, no analytics. Pure local view of what happened.
 */
class StatsActivity : BaseActivity() {

    private fun getWeekdays(): Array<String> = arrayOf(
        getString(R.string.weekday_mon),
        getString(R.string.weekday_tue),
        getString(R.string.weekday_wed),
        getString(R.string.weekday_thu),
        getString(R.string.weekday_fri),
        getString(R.string.weekday_sat),
        getString(R.string.weekday_sun),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        bindAll()
    }

    override fun onResume() {
        super.onResume()
        bindAll()
    }

    private fun bindAll() {
        bindHeadline()
        bindChart()
        bindSenders()
        bindSources()
        wireButtons()
    }

    private fun bindHeadline() {
        findViewById<TextView>(R.id.stat_total).text =
            OtpStats.readAllEvents(this).size.toLocaleString()
        findViewById<TextView>(R.id.stat_today).text =
            OtpStats.dailyCounts(this, 1)[0].toLocaleString()
    }

    private fun bindChart() {
        val chart = findViewById<BarChartView>(R.id.chart_week)
        val data = OtpStats.dailyCounts(this, 7)
        val reversed = IntArray(data.size)
        val labels = Array(data.size) { "" }
        val c = Calendar.getInstance()
        val weekdays = getWeekdays()
        for (i in data.indices) {
            reversed[i] = data[data.size - 1 - i]
            val cal = c.clone() as Calendar
            cal.add(Calendar.DAY_OF_MONTH, -(data.size - 1 - i))
            val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
            val idx = (dow + 5) % 7 // → 0=Mon..6=Sun
            labels[i] = weekdays[idx]
        }
        chart.setData(reversed, labels)
    }

    private fun bindSenders() {
        val container = findViewById<LinearLayout>(R.id.senders_container)
        container.removeAllViews()
        val top = OtpStats.topSenders(this, 12)
        val senderToPkg = OtpStats.senderToPkg(this)
        if (top.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.stats_empty)
                setTextColor(ContextCompat.getColor(this@StatsActivity, R.color.stats_empty))
                textSize = 12f
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            container.addView(empty)
            return
        }
        val max = top[0].count
        val inflater = LayoutInflater.from(this)
        for (t in top) {
            val row = inflater.inflate(R.layout.item_stats_sender, container, false)
            val name = row.findViewById<TextView>(R.id.tv_sender)
            val count = row.findViewById<TextView>(R.id.tv_count)
            val bar = row.findViewById<View>(R.id.bar)
            val icon = row.findViewById<ImageView>(R.id.iv_icon)
            name.text = t.sender
            count.text = t.count.toLocaleString()
            // Bar width is set on the layout params proportionally
            // to count/max.
            val maxBarW = dp(180)
            val barW = if (max > 0) (maxBarW * (t.count / max.toFloat())).toInt() else dp(2)
            val lp = bar.layoutParams
            lp.width = maxOf(dp(4), barW)
            bar.layoutParams = lp

            // Resolve sender → app icon. Senders that came from SMS
            // have no pkg — they get a generic SMS bubble.
            val pkg = senderToPkg[t.sender]
            val d: Drawable? = if (!pkg.isNullOrEmpty()) {
                try {
                    packageManager.getApplicationIcon(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            } else {
                null
            }
            if (d != null) {
                icon.setImageDrawable(d)
                icon.visibility = View.VISIBLE
            } else {
                icon.setImageResource(R.drawable.ic_sms)
                icon.alpha = 0.6f
                icon.visibility = View.VISIBLE
            }
            container.addView(row)
        }
    }

    private fun bindSources() {
        val container = findViewById<LinearLayout>(R.id.sources_container)
        container.removeAllViews()
        var sms = 0
        var push = 0
        var test = 0
        for (e in OtpStats.readAllEvents(this)) {
            when (OtpSource.fromStorageId(e.source)) {
                OtpSource.SMS -> sms++
                OtpSource.PUSH -> push++
                OtpSource.TEST -> test++
                null -> Unit
            }
        }
        addSourceRow(container, "SMS", sms)
        addSourceRow(container, getString(R.string.stats_notifications), push)
        if (test > 0) addSourceRow(container, getString(R.string.stats_test), test)
    }

    private fun addSourceRow(parent: LinearLayout, label: String, count: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) }
        }

        val lab = TextView(this).apply {
            text = label
            setTextColor(ContextCompat.getColor(this@StatsActivity, R.color.stats_label))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
            )
        }
        row.addView(lab)

        val c = TextView(this).apply {
            text = count.toLocaleString()
            setTextColor(ContextCompat.getColor(this@StatsActivity, R.color.stats_count))
            textSize = 13f
            typeface = ResourcesCompat.getFont(this@StatsActivity, R.font.jetbrains_mono_bold)
        }
        row.addView(c)

        parent.addView(row)
    }

    private fun wireButtons() {
        findViewById<Button?>(R.id.btn_clear_stats)?.setOnClickListener {
            OtpStats.clear(this)
            bindAll()
            Toast.makeText(this, getString(R.string.toast_stats_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Int): Int = dp(v.toFloat())
}
