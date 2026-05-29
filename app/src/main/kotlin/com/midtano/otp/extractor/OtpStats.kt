// SPDX-License-Identifier: MIT
package com.midtano.otp.extractor

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.midtano.otp.R
import com.midtano.otp.system.CrashLogger
import com.midtano.otp.system.IoScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Calendar

/**
 * Lightweight on-device statistics about received OTPs.
 *
 * Persists into a dedicated [SharedPreferences] file so it does not
 * bloat the main `otp_prefs` store. No network, no analytics —
 * purely a local "how many codes did I get this week, who sent them"
 * dashboard for the user.
 *
 * Storage shape:
 * - `events` JSON array — `{ts, sender, source, pkg}` entries,
 *   capped at [MAX_EVENTS] so the file never grows past ~50 KB;
 *   older events roll off.
 * - `totals` JSON object keyed by sender → integer count.
 *
 * Every method is `@Synchronized` so concurrent writes from
 * [com.midtano.otp.service.SmsReceiver] and
 * [com.midtano.otp.service.NotificationListener] are safe.
 */
internal object OtpStats {

    private const val FILE = "otp_stats"
    private const val KEY_EVENTS = "events"
    private const val KEY_TOTALS = "totals"
    private const val MAX_EVENTS = 500

    /** A single received-OTP event for [readAllEvents]. */
    data class Event(
        val timestamp: Long = 0L,
        val sender: String = "",
        val source: String = "",
        val pkg: String = "",
    )

    /** Sender → count tuple for [topSenders]. */
    data class SenderTotal(
        val sender: String = "",
        val count: Int = 0,
    )

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Record one OTP event. Returns immediately; the actual JSON +
     * SharedPreferences write happens on [IoScope] so receiver and
     * listener threads stay snappy. Inside the coroutine the work is
     * still serialised through `synchronized(this)` so concurrent
     * dispatches cannot lose entries.
     */
    fun record(ctx: Context?, sender: String?, source: String?, pkg: String?) {
        if (ctx == null) return
        // Capture an application context up front so a service that
        // tears down between this call and the launched write does
        // not strand us with a dead context.
        val app = ctx.applicationContext
        val ts = System.currentTimeMillis()
        val safeSender = sender ?: ""
        val safeSource = source ?: ""
        val safePkg = pkg ?: ""
        IoScope.scope.launch {
            persist(app, ts, safeSender, safeSource, safePkg)
        }
    }

    @Synchronized
    private fun persist(
        ctx: Context,
        ts: Long,
        sender: String,
        source: String,
        pkg: String,
    ) {
        try {
            val s = sp(ctx)
            val events = readEvents(s)
            val e = JSONObject().apply {
                put("ts", ts)
                put("sender", sender)
                put("source", source)
                put("pkg", pkg)
            }
            events.put(e)
            // Keep at most MAX_EVENTS by rebuilding the array in one
            // pass when the cap is exceeded. The previous version
            // looped `events.remove(0)`, each call shifting every
            // remaining element by one — O(N²). One sweep stays O(N).
            val pruned: JSONArray = if (events.length() > MAX_EVENTS) {
                val out = JSONArray()
                val from = events.length() - MAX_EVENTS
                for (i in from until events.length()) {
                    out.put(events.opt(i))
                }
                out
            } else {
                events
            }
            val totals = readTotals(s)
            val key = if (sender.isEmpty()) ctx.getString(R.string.stats_unknown_sender) else sender
            val prev = totals.optInt(key, 0)
            totals.put(key, prev + 1)
            s.edit {
                putString(KEY_EVENTS, pruned.toString())
                putString(KEY_TOTALS, totals.toString())
            }
        } catch (e: CancellationException) {
            // Cooperative cancellation must propagate up so the
            // scope owner decides whether to swallow it.
            throw e
        } catch (e: Exception) {
            CrashLogger.logErr("OtpStats.persist failed", e)
        }
    }

    /** Return all stored events, oldest first. */
    @Synchronized
    fun readAllEvents(ctx: Context?): List<Event> {
        if (ctx == null) return emptyList()
        return try {
            val arr = readEvents(sp(ctx))
            val out = ArrayList<Event>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Event(
                        timestamp = o.optLong("ts", 0L),
                        sender = o.optString("sender", ""),
                        source = o.optString("source", ""),
                        pkg = o.optString("pkg", ""),
                    ),
                )
            }
            out
        } catch (ex: Exception) {
            CrashLogger.logErr("OtpStats.readAllEvents failed", ex)
            emptyList()
        }
    }

    /** Sender → count, sorted by count descending. */
    @Synchronized
    fun topSenders(ctx: Context?, limit: Int): List<SenderTotal> {
        if (ctx == null) return emptyList()
        val all = ArrayList<SenderTotal>()
        try {
            val totals = readTotals(sp(ctx))
            val it = totals.keys()
            while (it.hasNext()) {
                val key = it.next()
                val n = totals.optInt(key, 0)
                if (n <= 0) continue
                all.add(SenderTotal(sender = key, count = n))
            }
        } catch (ex: Exception) {
            CrashLogger.logErr("OtpStats.topSenders failed", ex)
        }
        all.sortByDescending { it.count }
        return if (limit > 0 && all.size > limit) ArrayList(all.subList(0, limit)) else all
    }

    /** Bucket recent events into N day-bins for a bar chart. Day 0 = today. */
    @Synchronized
    fun dailyCounts(ctx: Context?, days: Int): IntArray {
        val buckets = IntArray(maxOf(1, days))
        if (ctx == null) return buckets
        // Each event is bucketed by `(startOfToday - eventLocalMidnight) / dayMs`.
        // Computing the event's local midnight via Calendar keeps the
        // boundaries consistent across DST shifts, where naive
        // UTC-modulo arithmetic would drift by an hour.
        val dayMs = 24L * 3600L * 1000L
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        for (e in readAllEvents(ctx)) {
            cal.timeInMillis = e.timestamp
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val eventDayStart = cal.timeInMillis
            val delta = startOfToday - eventDayStart
            val dayBucket = (delta / dayMs).toInt()
            if (dayBucket < 0 || dayBucket >= buckets.size) continue
            buckets[dayBucket]++
        }
        return buckets
    }

    /** Sender → package map for icon resolution in the stats UI. */
    @Synchronized
    fun senderToPkg(ctx: Context?): Map<String, String> {
        if (ctx == null) return emptyMap()
        // Walk the events in chronological order and let the
        // freshest-by-timestamp record win for each sender. The
        // events JSON is appended in IoScope launch order, which
        // for two near-simultaneous record() calls is NOT
        // guaranteed to match the call-site order — the only
        // stable ordering is the timestamp captured up-front in
        // record(). Without sorting we'd return whichever
        // coroutine happened to land first, which is flaky on
        // multi-core CI runners.
        val latestTs = HashMap<String, Long>()
        val out = HashMap<String, String>()
        for (e in readAllEvents(ctx)) {
            if (e.pkg.isEmpty()) continue
            val previousTs = latestTs[e.sender]
            if (previousTs == null || e.timestamp >= previousTs) {
                latestTs[e.sender] = e.timestamp
                out[e.sender] = e.pkg
            }
        }
        return out
    }

    /** Drop the entire stats database. Wired to the "Clear" button. */
    @Synchronized
    fun clear(ctx: Context?) {
        if (ctx == null) return
        sp(ctx).edit {
            remove(KEY_EVENTS)
        remove(KEY_TOTALS)
        }
    }

    private fun readEvents(s: SharedPreferences): JSONArray {
        val raw = s.getString(KEY_EVENTS, null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (_: JSONException) {
            JSONArray()
        }
    }

    private fun readTotals(s: SharedPreferences): JSONObject {
        val raw = s.getString(KEY_TOTALS, null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: JSONException) {
            JSONObject()
        }
    }
}
