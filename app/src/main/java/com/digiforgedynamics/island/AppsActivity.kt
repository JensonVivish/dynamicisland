package com.digiforgedynamics.island

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Lets the user block individual apps from showing on the island. Checked = allowed. */
class AppsActivity : Activity() {

    private class Item(val pkg: String, val label: String, val icon: Drawable)
    private class Holder(val icon: ImageView, val name: TextView, val cb: CheckBox)

    private val items = ArrayList<Item>()
    private lateinit var list: ListView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = 0xFF0B0B10.toInt()
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bg)
        root.setPadding(dp(16), dp(24), dp(16), 0)

        val head = TextView(this)
        head.text = "Apps allowed on the island"
        head.textSize = 20f
        head.setTextColor(Color.WHITE)
        root.addView(head)
        val sub = TextView(this)
        sub.text = "Untick an app to stop it appearing here."
        sub.textSize = 12f
        sub.setTextColor(0xFF8E8E98.toInt())
        sub.setPadding(0, dp(2), 0, dp(10))
        root.addView(sub)

        list = ListView(this)
        list.divider = null
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        Thread {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val seen = HashSet<String>()
            val tmp = ArrayList<Item>()
            for (r in pm.queryIntentActivities(q, 0)) {
                val p = r.activityInfo.packageName
                if (p == packageName || !seen.add(p)) continue
                tmp.add(Item(p, r.loadLabel(pm).toString(), r.loadIcon(pm)))
            }
            tmp.sortBy { it.label.lowercase() }
            runOnUiThread {
                items.addAll(tmp)
                list.adapter = Adapter()
            }
        }.start()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row: LinearLayout
            val h: Holder
            if (convertView == null) {
                row = LinearLayout(this@AppsActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(8), 0, dp(8))
                val ic = ImageView(this@AppsActivity)
                val nm = TextView(this@AppsActivity)
                nm.setTextColor(Color.WHITE)
                nm.textSize = 15f
                val cb = CheckBox(this@AppsActivity)
                cb.isClickable = false
                cb.isFocusable = false
                row.addView(ic, LinearLayout.LayoutParams(dp(36), dp(36)))
                val np = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                np.marginStart = dp(14)
                row.addView(nm, np)
                row.addView(cb)
                h = Holder(ic, nm, cb)
                row.tag = h
            } else {
                row = convertView as LinearLayout
                h = row.tag as Holder
            }
            val item = items[position]
            h.icon.setImageDrawable(item.icon)
            h.name.text = item.label
            h.cb.isChecked = !Prefs.isBlocked(this@AppsActivity, item.pkg)
            row.setOnClickListener {
                val allowed = !h.cb.isChecked
                h.cb.isChecked = allowed
                Prefs.setBlocked(this@AppsActivity, items[position].pkg, !allowed)
            }
            return row
        }
    }
}
