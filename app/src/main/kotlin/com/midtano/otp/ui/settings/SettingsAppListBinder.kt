// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.midtano.otp.R
import com.midtano.otp.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Owns the per-app filter section of the Settings screen: search
 * box, select-all / select-none buttons, and the recyclable list
 * of checkbox + icon rows.
 *
 * Caches the expensive parts (icon LRU, lazy `PackageManager`
 * query, RecyclerView setup) without polluting `SettingsActivity`'s
 * field-level state.
 */
internal class SettingsAppListBinder(
    private val host: SettingsActivity,
    private val lifecycleScope: LifecycleCoroutineScope,
) {

    private val apps = ArrayList<AppInfo>()
    private var adapter: AppsAdapter? = null
    private var recycler: RecyclerView? = null
    private var selected: MutableSet<String> = HashSet()
    private var appQuery: String = ""
    private var loaded: Boolean = false

    /**
     * Icon cache — 256 entries keep two screens of rows resident
     * without thrashing PackageManager. Each entry is a few KB so
     * the worst case is a couple of MB; trivially small next to
     * the RecyclerView itself.
     */
    private val iconCache = LruCache<String, Drawable>(256)

    private val uiHandler = Handler(Looper.getMainLooper())
    private val applyFilterRunnable = Runnable { adapter?.setQuery(appQuery) }

    /** Wire the search box and bulk-select buttons. */
    fun bindControls() {
        host.findViewById<EditText?>(R.id.et_app_search)?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                appQuery = s?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                uiHandler.removeCallbacks(applyFilterRunnable)
                uiHandler.postDelayed(applyFilterRunnable, SEARCH_DEBOUNCE_MS)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        host.findViewById<Button?>(R.id.btn_apps_select_all)?.setOnClickListener {
            adapter?.bulkToggleVisible(true)
        }
        host.findViewById<Button?>(R.id.btn_apps_select_none)?.setOnClickListener {
            adapter?.bulkToggleVisible(false)
        }
        // Initial selection + visibility based on current Prefs.
        selected = HashSet(Prefs.getAllowedApps(host))
        toggleSection(Prefs.isFilterApps(host))
    }

    /**
     * Show / hide the per-app filter section. While the filter is
     * off the ScrollView does not host 100+ checkbox + icon rows,
     * so the toggle itself fires instantly. The first ON triggers
     * an async load; subsequent flips are pure visibility changes.
     */
    fun toggleSection(on: Boolean) {
        val section = host.findViewById<View?>(R.id.apps_filter_section) ?: return
        if (!on) {
            section.visibility = View.GONE
            return
        }
        if (!loaded) {
            loaded = true
            loadAsync()
        }
        section.visibility = View.VISIBLE
    }

    /**
     * Initialise the apps RecyclerView. Uses [AppsAdapter] so only
     * visible rows (~10 per screen) are rendered while the rest
     * are recycled.
     */
    private fun initRecyclerOnce() {
        if (adapter != null) return
        val rv = host.findViewById<RecyclerView?>(R.id.apps_recycler) ?: return
        recycler = rv
        adapter = AppsAdapter(host, selected, iconCache, lifecycleScope) { newSelected ->
            selected = HashSet(newSelected)
            Prefs.setAllowedApps(host, selected)
        }
        rv.setHasFixedSize(true)
        rv.layoutManager = LinearLayoutManager(host)
        // Generous recycle pool so ViewHolders survive scrolling
        // through long app lists without re-inflation.
        rv.setItemViewCacheSize(20)
        rv.adapter = adapter
    }

    /**
     * Cancel the debounced search runnable.
     *
     * Called from [SettingsActivity.onDestroy] so a queued filter
     * runnable cannot fire after the activity is gone (the
     * runnable captures `this`, which captures the activity, so
     * skipping this would let the system's main `Handler` keep
     * the activity reachable for the length of the debounce
     * window — minor in practice, but still a leak we shouldn't
     * be the cause of).
     */
    fun dispose() {
        uiHandler.removeCallbacks(applyFilterRunnable)
    }

    /**
     * Heavy `PackageManager.queryIntentActivities` call runs on the
     * IO dispatcher; results are dispatched back to the UI thread
     * before touching the RecyclerView. Tied to the activity
     * lifecycle through [lifecycleScope], so a rotation /
     * `onDestroy` cancels the in-flight load automatically.
     */
    private fun loadAsync() {
        lifecycleScope.launch {
            val built = withContext(Dispatchers.IO) {
                val pm = host.packageManager
                // Android 11+ package-visibility rules — querying for
                // launcher activities is the documented replacement
                // for `getInstalledApplications`. Matches the
                // `<queries>` declaration in AndroidManifest.xml so
                // no app gets filtered out by visibility rules.
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val launchers = pm.queryIntentActivities(launcherIntent, 0)
                val out = ArrayList<AppInfo>(launchers.size)
                val seen = HashSet<String>(launchers.size)

                for (ri in launchers) {
                    val activityInfo = ri.activityInfo ?: continue
                    val ai = activityInfo.applicationInfo ?: continue
                    if (ai.packageName == host.packageName) continue
                    if (!seen.add(ai.packageName)) continue
                    val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdated = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    if (isSystem && !isUpdated) continue
                    val name = pm.getApplicationLabel(ai).toString()
                    out.add(
                        AppInfo(
                            pkg = ai.packageName,
                            name = name,
                            // Locale.ROOT — see AppsAdapter.setQuery.
                            nameLower = name.lowercase(Locale.ROOT),
                        ),
                    )
                }
                out.sortBy { it.nameLower }
                out
            }

            apps.clear()
            apps.addAll(built)
            initRecyclerOnce()
            adapter?.setApps(built)
            adapter?.setQuery(appQuery)
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS: Long = 80L
    }
}
