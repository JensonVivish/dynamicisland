package com.digiforgedynamics.island

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bgColor = 0xFF0B0B10.toInt()
    private val cardColor = 0xFF17171F.toInt()
    private val accent = 0xFF22D3EE.toInt()
    private val gray = 0xFF8E8E98.toInt()
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private val refreshers = ArrayList<() -> Unit>()
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        val sv = ScrollView(this)
        sv.setBackgroundColor(bgColor)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(28), dp(20), dp(40))
        sv.addView(root)
        setContentView(sv)
        build()
    }

    override fun onResume() {
        super.onResume()
        for (r in refreshers) r()
        if (Prefs.enabled(this) && Settings.canDrawOverlays(this)) startSvc()
    }

    private fun build() {
        root.addView(label("DigiIsland", 30f, Color.WHITE, true))
        root.addView(label("A lightweight Dynamic Island for Android", 14f, gray), lp(2, 22))

        header("Setup")
        statusRow(
            "Display over other apps",
            "Lets the island appear on top of any app",
            { Settings.canDrawOverlays(this) }
        ) {
            go(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        statusRow(
            "Notification access",
            "Reads notifications and media so they can be shown",
            {
                getSystemService(NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(ComponentName(this, IslandListener::class.java))
            }
        ) {
            go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            statusRow(
                "Service notification",
                "Silent, minimised - keeps the island running",
                { checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        statusRow(
            "Unrestricted battery",
            "Stops Android from killing the island in the background",
            { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) }
        ) {
            go(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }

        val note = card()
        note.orientation = LinearLayout.VERTICAL
        note.addView(
            label(
                "Can't enable Notification access? On Android 13+ a sideloaded app is blocked until you open " +
                    "App info, tap the three-dot menu and choose \"Allow restricted settings\".",
                12f, gray
            )
        )
        val appInfo = button("Open App info", 0xFF2A2A33.toInt(), Color.WHITE) {
            go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        note.addView(appInfo, lp(10, 0))
        root.addView(note, lp(0, 10))

        header("Island")
        switchRow("Enable island", Prefs.ENABLED, true) { on ->
            if (on) startSvc() else stopService(Intent(this, KeepAliveService::class.java))
        }
        switchRow("Show notifications", Prefs.NOTIFS, true)
        switchRow("Show music controls", Prefs.MEDIA, true)
        switchRow("Show charging", Prefs.CHARGING, true)
        switchRow("Hide in landscape", Prefs.HIDE_LANDSCAPE, true)

        header("Position")
        sliderRow("Distance from top", Prefs.TOP, 8, 0, 80, " dp")
        sliderRow("Horizontal offset", Prefs.X, 0, -150, 150, " dp")
        sliderRow("Show time", Prefs.HOLD, 5, 2, 10, " s")

        header("Try it")
        root.addView(button("Test island", accent, Color.BLACK) { test() }, lp(0, 10))
        root.addView(button("Choose apps", 0xFF2A2A33.toInt(), Color.WHITE) {
            go(Intent(this, AppsActivity::class.java))
        }, lp(0, 10))
        root.addView(
            label(
                "Tap the island to expand, swipe up to dismiss, long-press to open the app.",
                12f, gray
            ), lp(14, 0)
        )
    }

    private fun test() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant \"Display over other apps\" first", Toast.LENGTH_SHORT).show()
            return
        }
        val ctl = IslandController.get(this)
        ctl.show(
            IslandEvent(
                key = "test",
                title = "DigiIsland",
                text = "This is how your notifications will look.",
                iconRes = android.R.drawable.ic_dialog_info,
                actions = listOf(IslandAction("Nice", 0, 0) { ctl.remove("test") }),
                startExpanded = true
            ), Prefs.holdMs(this)
        )
    }

    private fun startSvc() {
        try {
            startForegroundService(Intent(this, KeepAliveService::class.java))
        } catch (e: Exception) {
        }
    }

    private fun go(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open that settings page", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- small UI helpers --------------------------------------------------------------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun lp(top: Int, bottom: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(match, wrap)
        p.topMargin = dp(top)
        p.bottomMargin = dp(bottom)
        return p
    }

    private fun rounded(color: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun header(s: String) {
        root.addView(label(s.uppercase(), 12f, accent, true), lp(18, 8))
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        c.background = rounded(cardColor, 16)
        return c
    }

    private fun button(text: String, bg: Int, fg: Int, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = text
        b.setTextColor(fg)
        b.textSize = 14f
        b.setTypeface(b.typeface, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(16), dp(11), dp(16), dp(11))
        b.background = rounded(bg, 22)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun statusRow(title: String, sub: String, check: () -> Boolean, act: () -> Unit) {
        val row = card()
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(title, 15f, Color.WHITE, true))
        col.addView(label(sub, 12f, gray))
        row.addView(col, LinearLayout.LayoutParams(0, wrap, 1f))
        val btn = button("Grant", accent, Color.BLACK) { act() }
        row.addView(btn, LinearLayout.LayoutParams(wrap, wrap))
        root.addView(row, lp(0, 10))
        refreshers.add {
            val done = check()
            btn.text = if (done) "Granted" else "Grant"
            btn.background = rounded(if (done) 0xFF2A2A33.toInt() else accent, 22)
            btn.setTextColor(if (done) gray else Color.BLACK)
        }
    }

    private fun switchRow(name: String, key: String, def: Boolean, onChange: (Boolean) -> Unit = {}) {
        val row = card()
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(label(name, 15f, Color.WHITE), LinearLayout.LayoutParams(0, wrap, 1f))
        val sw = Switch(this)
        sw.isChecked = Prefs.bool(this, key, def)
        sw.setOnCheckedChangeListener { _, v ->
            Prefs.setBool(this, key, v)
            onChange(v)
            IslandController.get(this).refresh()
        }
        row.addView(sw)
        root.addView(row, lp(0, 10))
    }

    private fun sliderRow(name: String, key: String, def: Int, min: Int, max: Int, unit: String) {
        val row = card()
        row.orientation = LinearLayout.VERTICAL
        val cur = Prefs.int(this, key, def)
        val tv = label("$name: $cur$unit", 14f, Color.WHITE)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = cur - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress + min
                tv.text = "$name: $v$unit"
                if (fromUser) {
                    Prefs.setInt(this@MainActivity, key, v)
                    IslandController.get(this@MainActivity).applySettings()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        row.addView(tv)
        row.addView(sb, LinearLayout.LayoutParams(match, wrap))
        root.addView(row, lp(0, 10))
    }
}
