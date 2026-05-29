// SPDX-License-Identifier: MIT
package com.midtano.otp.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.midtano.otp.R
import com.midtano.otp.system.CrashLogger
import com.midtano.otp.widget.AnimatedCheckbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * RecyclerView adapter for the installed-apps list shown in the
 * package filter.
 *
 * Uses [DiffUtil] so search-filter changes animate row additions
 * and removals smoothly instead of flashing the full list, and so
 * per-row binds aren't paid for rows that didn't change.
 */
internal class AppsAdapter(
    ctx: Context,
    initialSelected: Set<String>,
    private val iconCache: LruCache<String, Drawable>,
    private val scope: CoroutineScope,
    private val listener: OnSelectionChanged?,
) : RecyclerView.Adapter<AppsAdapter.AppHolder>() {

    /** Callback invoked when a row is tapped — toggles the selection. */
    fun interface OnSelectionChanged {
        fun onChanged(selected: Set<String>)
    }

    /** Full installed-apps list. */
    private val all = ArrayList<AppInfo>()

    /** Search-filtered list. ViewHolders bind only against this list. */
    private val filtered = ArrayList<AppInfo>()

    /** Current selection (allowed apps). */
    private val selected: HashSet<String> = HashSet(initialSelected)

    /** Lower-cased search text. */
    private var query: String = ""

    private val appCtx: Context = ctx.applicationContext
    private val inflater: LayoutInflater = LayoutInflater.from(ctx)

    init {
        setHasStableIds(true)
    }

    fun setApps(apps: List<AppInfo>) {
        all.clear()
        all.addAll(apps)
        applyFilter()
    }

    fun setQuery(q: String?) {
        // Locale.ROOT keeps the apps-list filter case-folding stable
        // across device locales (Turkish "İ"/"ı" otherwise breaks
        // ASCII substring searches).
        query = q?.trim()?.lowercase(Locale.ROOT) ?: ""
        applyFilter()
    }

    private fun applyFilter() {
        val next = if (query.isEmpty()) {
            ArrayList(all)
        } else {
            all.filterTo(ArrayList(all.size)) { a ->
                a.nameLower?.contains(query) == true ||
                    a.pkg?.lowercase(Locale.ROOT)?.contains(query) == true
            }
        }
        val diff = DiffUtil.calculateDiff(
            AppDiffCallback(filtered, next, selected, selected),
            /* detectMoves = */
            false,
        )
        filtered.clear()
        filtered.addAll(next)
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Apply a bulk action (select all / deselect all) to the
     * currently visible (filtered) rows only.
     */
    fun bulkToggleVisible(add: Boolean) {
        val previous = HashSet(selected)
        for (a in filtered) {
            val pkg = a.pkg ?: continue
            if (add) selected.add(pkg) else selected.remove(pkg)
        }
        listener?.onChanged(selected)
        // Diff against the unchanged list — only the selection state differs.
        val diff = DiffUtil.calculateDiff(
            AppDiffCallback(filtered, filtered, previous, selected),
            /* detectMoves = */
            false,
        )
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        val a = filtered[position]
        return a.pkg?.hashCode()?.toLong() ?: position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder {
        val row = inflater.inflate(R.layout.item_app, parent, false)
        return AppHolder(row)
    }

    override fun onBindViewHolder(h: AppHolder, position: Int) {
        val app = filtered[position]
        h.tvName.text = app.name
        h.cb.setChecked(app.pkg in selected, false)

        // Icon — from cache when present, otherwise placeholder + async load.
        val cached = iconCache[app.pkg]
        if (cached != null) {
            h.icon.setImageDrawable(cached)
        } else {
            h.icon.setImageDrawable(null)
            val pkg = app.pkg ?: return
            val iconRef = h.icon
            scope.launch {
                val d: Drawable? = withContext(Dispatchers.IO) {
                    try {
                        appCtx.packageManager.getApplicationIcon(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                        // Package was uninstalled between the list
                        // snapshot and the bind. Silent no-op; the
                        // placeholder stays.
                        null
                    } catch (e: android.content.res.Resources.NotFoundException) {
                        // Package present but its icon resource was
                        // stripped (rare on AdaptiveIconDrawable
                        // OEMs) — keep the placeholder.
                        CrashLogger.logErr("AppsAdapter icon load failed for $pkg", e)
                        null
                    }
                }
                if (d != null) {
                    iconCache.put(pkg, d)
                    // Make sure the ViewHolder is still bound to the
                    // same package before painting — it may have
                    // been recycled while the load was in flight.
                    if (pkg == iconRef.getTag(R.id.iv_app_icon)) {
                        iconRef.setImageDrawable(d)
                    }
                }
            }
            h.icon.setTag(R.id.iv_app_icon, app.pkg)
        }

        h.itemView.setOnClickListener {
            val pkg = app.pkg ?: return@setOnClickListener
            if (pkg in selected) selected.remove(pkg) else selected.add(pkg)
            listener?.onChanged(selected)
            h.cb.isChecked = pkg in selected
        }
    }

    override fun getItemCount(): Int = filtered.size

    class AppHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.iv_app_icon)
        val tvName: TextView = itemView.findViewById(R.id.tv_app_name)
        val cb: AnimatedCheckbox = itemView.findViewById<AnimatedCheckbox>(R.id.cb_app).apply {
            isClickable = false
            isFocusable = false
        }
    }

    /**
     * Compares two snapshots of the filtered apps list plus their
     * selection sets so [DiffUtil] can both move rows around AND
     * repaint the checkbox when only the selection changed.
     */
    private class AppDiffCallback(
        private val oldList: List<AppInfo>,
        private val newList: List<AppInfo>,
        oldSelected: Set<String>,
        newSelected: Set<String>,
    ) : DiffUtil.Callback() {
        private val oldSelected: Set<String> = HashSet(oldSelected)
        private val newSelected: Set<String> = HashSet(newSelected)

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
            val a = oldList[oldPos].pkg
            val b = newList[newPos].pkg
            return a == b
        }

        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
            val a = oldList[oldPos]
            val b = newList[newPos]
            val nameSame = a.name == b.name
            val checkSame = (a.pkg in oldSelected) == (b.pkg in newSelected)
            return nameSame && checkSame
        }
    }
}
