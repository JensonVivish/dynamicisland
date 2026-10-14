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
import android.widget.*

class MainActivity : Activity() {

    private val bgCard   = 0xFF17171F.toInt()
    private val bg       = 0xFF0A0A10.toInt()
    private val accent   = 0xFF22D3EE.toInt()
    private val gray     = 0xFF8E8E98.toInt()
    private val match    = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap     = ViewGroup.LayoutParams.WRAP_CONTENT
    private val refreshers = ArrayList<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        val sv = ScrollView(this); sv.setBackgroundColor(bg)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(28), dp(20), dp(48))
        sv.addView(root); setContentView(sv)
        buildUI(root)
    }

    override fun onResume() {
        super.onResume()
        refreshers.forEach { it() }
        startSvc()
    }

    private fun buildUI(root: LinearLayout) {
        root.addView(tv("DigiIsland", 28f, Color.WHITE, bold = true))
        root.addView(tv("Your phone's Dynamic Island", 13f, gray), lp(4, 24))

        // ── Setup ──────────────────────────────────────────────────────────
        section(root, "SETUP REQUIRED")
        permRow(root, "Display over other apps",
            "Lets the island appear on top of everything",
            { Settings.canDrawOverlays(this) }
        ) { go(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))) }

        permRow(root, "Notification access",
            "Reads notifications so they can show on the island",
            { nm().isNotificationListenerAccessGranted(
                ComponentName(this, IslandListener::class.java)) }
        ) { go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        if (Build.VERSION.SDK_INT >= 33) {
            permRow(root, "Post notifications",
                "Shows the minimised service notification",
                { checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED }
            ) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
        }

        permRow(root, "Unrestricted battery",
            "Prevents Android from killing the island",
            { getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(packageName) }
        ) { go(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"))) }


        // hint for sideloaded apps
        val hint = card()
        hint.addView(tv("On Android 13+, sideloaded apps need: App info → ⋮ → Allow restricted settings, then grant Notification access.", 12f, gray))
        val hBtn = btn("Open App info", 0xFF252530.toInt(), Color.WHITE) {
            go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
        hint.addView(hBtn, lp(8, 0))
        root.addView(hint, lp(0, 10))

        // ── Island ────────────────────────────────────────────────────────
        section(root, "ISLAND")
        swRow(root, "Enable DigiIsland",         Prefs.ENABLED,      true) { on ->
            if (on) startSvc() else stopService(Intent(this, KeepAliveService::class.java))
        }
        swRow(root, "Notifications",             Prefs.NOTIFS,       true)
        swRow(root, "Music controls",            Prefs.MEDIA,        true)
        swRow(root, "Battery & charging",        Prefs.CHARGING,     true)
        swRow(root, "Camera & mic privacy dots", Prefs.PRIVACY_DOTS, true)
        swRow(root, "Bluetooth accessory battery",Prefs.BT_BATTERY,  true)
        swRow(root, "Navigation (Maps / Waze)",  Prefs.NAV,          true)
        swRow(root, "Live tiles (Uber, Swiggy, flights…)", Prefs.LIVE_TILES, true)
        swRow(root, "Hide in landscape",         Prefs.HIDE_LANDSCAPE, true)
        swRow(root, "Extra keep-alive (adds a silent notification)", Prefs.KEEP_ALIVE, false) { on ->
            if (on) startSvc() else stopService(Intent(this, KeepAliveService::class.java))
        }

        // ── Position ──────────────────────────────────────────────────────
        section(root, "POSITION")
        sliderRow(root, "Distance from top", Prefs.TOP,  8,  0,  120, " dp")
        sliderRow(root, "Horizontal offset", Prefs.X,    0, -180, 180, " dp")
        sliderRow(root, "Show duration",     Prefs.HOLD, 5,  2,   15,  " s")

        // ── Try ───────────────────────────────────────────────────────────
        section(root, "TRY IT")
        root.addView(btn("Test notification", accent, Color.BLACK) { testNotif() }, lp(0, 8))
        root.addView(btn("Test media controls", 0xFF252530.toInt(), Color.WHITE) { testMedia() }, lp(0, 8))
        root.addView(btn("Test charging", 0xFF252530.toInt(), Color.WHITE) { testCharge() }, lp(0, 8))
        root.addView(btn("Blocked apps", 0xFF252530.toInt(), Color.WHITE) {
            go(Intent(this, AppsActivity::class.java)) }, lp(0, 8))

        root.addView(tv("Tap = expand · Long press = expand + hold · Swipe up or sideways = dismiss",
            12f, gray), lp(12, 0))
    }

    // ── Test events ───────────────────────────────────────────────────────────

    private fun testNotif() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant \"Display over other apps\" first", Toast.LENGTH_SHORT).show()
            return
        }
        IslandController.get(this).show(
            IslandEvent(
                key          = "test_notif",
                title        = "DigiIsland",
                text         = "Notifications look like this — tap to expand, swipe to dismiss.",
                iconRes      = android.R.drawable.ic_dialog_info,
                iconTint     = 0xFF22D3EE.toInt(),
                style        = IslandStyle.DEFAULT,
                actions      = listOf(IslandAction("Got it", 0, 0) {
                    IslandController.get(this).remove("test_notif") }),
                startExpanded= true
            )
        )
    }

    private fun testMedia() {
        IslandController.get(this).setPersistent(
            IslandEvent(
                key    = "media:test|Demo Track",
                title  = "Demo Track",
                text   = "DigiIsland Music Player",
                iconRes= android.R.drawable.ic_media_play,
                iconTint=0xFF22D3EE.toInt(),
                style  = IslandStyle.MEDIA,
                actions= listOf(
                    IslandAction(null, android.R.drawable.ic_media_previous, 0) {},
                    IslandAction(null, android.R.drawable.ic_media_pause,    0) {
                        IslandController.get(this).setPersistent(null) },
                    IslandAction(null, android.R.drawable.ic_media_next,     0) {}
                ),
                sticky = true
            )
        )
    }

    private fun testCharge() {
        IslandController.get(this).show(
            IslandEvent(
                key          = "test_charge",
                title        = "Charging",
                text         = "Your device is charging",
                iconRes      = android.R.drawable.ic_lock_idle_charging,
                iconTint     = Color.rgb(52, 199, 89),
                accessoryText= "87%",
                accessoryColor=Color.rgb(52, 199, 89),
                style        = IslandStyle.DEFAULT
            ), 4000
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun startSvc() {
        if (!Prefs.enabled(this) || !Prefs.keepAlive(this) || !Settings.canDrawOverlays(this)) return
        try { startForegroundService(Intent(this, KeepAliveService::class.java)) }
        catch (_: Exception) {}
    }
    private fun go(i: Intent) {
        try { startActivity(i) }
        catch (_: Exception) { Toast.makeText(this, "Couldn't open that screen", Toast.LENGTH_SHORT).show() }
    }
    private fun nm() = getSystemService(NotificationManager::class.java)
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun lp(topDp: Int, botDp: Int) = LinearLayout.LayoutParams(match, wrap).also {
        it.topMargin = dp(topDp); it.bottomMargin = dp(botDp) }

    private fun tv(s: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        val g = GradientDrawable(); g.setColor(bgCard); g.cornerRadius = dp(16).toFloat()
        background = g
    }

    private fun btn(text: String, bgCol: Int, fgCol: Int, onClick: () -> Unit) =
        TextView(this).apply {
            this.text = text; setTextColor(fgCol); textSize = 14f
            setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            setPadding(dp(16), dp(11), dp(16), dp(11))
            val g = GradientDrawable(); g.setColor(bgCol); g.cornerRadius = dp(22).toFloat()
            background = g; setOnClickListener { onClick() }
        }

    private fun section(root: LinearLayout, s: String) {
        val t = tv(s, 11f, accent, bold = true)
        val lp2 = lp(18, 8)
        root.addView(t, lp2)
    }

    private fun permRow(root: LinearLayout, name: String, sub: String,
                        check: () -> Boolean, act: () -> Unit) {
        val row = card(); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        col.addView(tv(name, 14.5f, Color.WHITE, bold = true))
        col.addView(tv(sub, 11.5f, gray).also {
            val lp2 = LinearLayout.LayoutParams(match, wrap); lp2.topMargin = dp(2)
            it.layoutParams = lp2
        })
        row.addView(col, LinearLayout.LayoutParams(0, wrap, 1f))
        val b = btn("Grant", accent, Color.BLACK) { act() }
        row.addView(b, LinearLayout.LayoutParams(wrap, wrap))
        root.addView(row, lp(0, 10))
        refreshers.add {
            val done = check()
            b.text = if (done) "✓" else "Grant"
            val bgC = if (done) 0xFF1A3A1A.toInt() else accent
            val fgC = if (done) Color.rgb(52,199,89) else Color.BLACK
            val g = GradientDrawable(); g.setColor(bgC); g.cornerRadius = dp(22).toFloat()
            b.background = g; b.setTextColor(fgC)
        }
    }

    private fun swRow(root: LinearLayout, name: String, key: String, def: Boolean,
                      onChange: (Boolean) -> Unit = {}) {
        val row = card(); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
        row.addView(tv(name, 14.5f, Color.WHITE), LinearLayout.LayoutParams(0, wrap, 1f))
        val sw = Switch(this); sw.isChecked = Prefs.bool(this, key, def)
        sw.setOnCheckedChangeListener { _, v ->
            Prefs.setBool(this, key, v); onChange(v)
            IslandController.get(this).refresh()
        }
        row.addView(sw); root.addView(row, lp(0, 10))
    }

    private fun sliderRow(root: LinearLayout, name: String, key: String,
                          def: Int, min: Int, max: Int, unit: String) {
        val row = card(); row.orientation = LinearLayout.VERTICAL
        val cur = Prefs.int(this, key, def)
        val label = tv("$name:  $cur$unit", 13.5f, Color.WHITE)
        val sb = SeekBar(this); sb.max = max - min; sb.progress = cur - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + min; label.text = "$name:  $v$unit"
                if (fromUser) { Prefs.setInt(this@MainActivity, key, v)
                    IslandController.get(this@MainActivity).applySettings() }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(label); row.addView(sb, LinearLayout.LayoutParams(match, wrap))
        root.addView(row, lp(0, 10))
    }
}
