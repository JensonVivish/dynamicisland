package com.digiforgedynamics.island

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings

/** Recent notifications, kept in memory only (never written to disk). */
object NotifHistory {
    private val items = ArrayList<String>()
    fun add(s: String) {
        items.add(0, s)
        if (items.size > 10) items.removeAt(items.size - 1)
    }
    fun text(): String = if (items.isEmpty()) "Nothing yet" else items.take(6).joinToString("\n")
}

/** The menu that opens when you tap the idle island. */
object QuickMenu {
    private val h = Handler(Looper.getMainLooper())
    private var torchOn = false
    private var timerRun: Runnable? = null
    private val laps = ArrayList<String>()

    fun open(ctx: Context) {
        val ctl = IslandController.get(ctx)
        val acts = listOf(
            IslandAction("🔦\nTorch") { ctl.remove("menu"); torch(ctx) },
            IslandAction("⏱\nTimer") { ctl.remove("menu"); pickTimer(ctx) },
            IslandAction("⏲\nStopwatch") { ctl.remove("menu"); stopwatch(ctx) },
            IslandAction("🔋\nBattery") { ctl.remove("menu"); battery(ctx) },
            IslandAction("📶\nInternet") { ctl.remove("menu"); panel(ctx, Settings.Panel.ACTION_INTERNET_CONNECTIVITY) },
            IslandAction("🔊\nVolume") { ctl.remove("menu"); panel(ctx, Settings.Panel.ACTION_VOLUME) },
            IslandAction("🔔\nHistory") { ctl.remove("menu"); history(ctx) },
            IslandAction("⚙\nSettings") { ctl.remove("menu"); launch(ctx, Intent(ctx, MainActivity::class.java)) }
        )
        ctl.show(
            IslandEvent(
                key = "menu", title = "Quick actions",
                iconRes = android.R.drawable.ic_menu_more,
                style = IslandStyle.MENU, actions = acts,
                sticky = true, startExpanded = true, userInitiated = true
            ), 0
        )
    }

    private fun launch(ctx: Context, i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    private fun panel(ctx: Context, action: String) = launch(ctx, Intent(action))

    private fun notice(ctx: Context, title: String, text: String? = null, ms: Long = 3000, acc: String? = null) {
        IslandController.get(ctx).show(
            IslandEvent(
                key = "notice", title = title, text = text,
                iconRes = android.R.drawable.ic_dialog_info, iconTint = 0xFF22D3EE.toInt(),
                accessoryText = acc, startExpanded = text != null, userInitiated = true
            ), ms
        )
    }

    private fun torch(ctx: Context) {
        try {
            val cm = ctx.getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            notice(ctx, if (torchOn) "Torch on" else "Torch off", null, 1800)
        } catch (e: Exception) {
            notice(ctx, "Torch unavailable", null, 2000)
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100)
    }

    private fun pickTimer(ctx: Context) {
        val ctl = IslandController.get(ctx)
        val acts = listOf(1, 5, 10, 15, 30, 60).map { m ->
            IslandAction("⏱\n$m min") { ctl.remove("timer_pick"); startTimer(ctx, m) }
        }
        ctl.show(
            IslandEvent(
                key = "timer_pick", title = "Set timer",
                iconRes = android.R.drawable.ic_menu_recent_history,
                style = IslandStyle.MENU, actions = acts,
                sticky = true, startExpanded = true, userInitiated = true
            ), 0
        )
    }

    private fun cancelTimer(ctx: Context) {
        timerRun?.let { h.removeCallbacks(it) }
        timerRun = null
        IslandController.get(ctx).remove("timer")
    }

    private fun startTimer(ctx: Context, min: Int) {
        val ctl = IslandController.get(ctx)
        timerRun?.let { h.removeCallbacks(it) }
        val len = min * 60_000L
        val end = SystemClock.elapsedRealtime() + len
        ctl.setOngoing(
            IslandEvent(
                key = "timer", title = "Timer", text = "$min min",
                iconRes = android.R.drawable.ic_menu_recent_history, iconTint = 0xFFFF9F0A.toInt(),
                style = IslandStyle.TIMER, chronoBase = end, chronoCountDown = true, sticky = true,
                actions = listOf(IslandAction("Cancel") { cancelTimer(ctx) }), userInitiated = true
            )
        )
        val r = Runnable {
            ctl.setOngoing(null)
            try {
                RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.play()
            } catch (e: Exception) {}
            ctl.show(
                IslandEvent(
                    key = "timer_done", title = "Timer done", text = "$min min finished",
                    iconRes = android.R.drawable.ic_lock_idle_alarm, iconTint = 0xFFFF9F0A.toInt(),
                    actions = listOf(IslandAction("Dismiss") { ctl.remove("timer_done") }),
                    sticky = true, startExpanded = true, userInitiated = true
                ), 0
            )
        }
        timerRun = r
        h.postDelayed(r, len)
    }

    private fun stopwatch(ctx: Context) {
        val ctl = IslandController.get(ctx)
        val base = SystemClock.elapsedRealtime()
        laps.clear()
        fun draw() {
            ctl.setOngoing(
                IslandEvent(
                    key = "stopwatch", title = "Stopwatch",
                    text = if (laps.isEmpty()) null else laps.takeLast(3).joinToString("\n"),
                    iconRes = android.R.drawable.ic_menu_recent_history, iconTint = 0xFF22D3EE.toInt(),
                    style = IslandStyle.TIMER, chronoBase = base, sticky = true,
                    actions = listOf(
                        IslandAction("Lap") {
                            laps.add("Lap " + (laps.size + 1) + "   " + fmt(SystemClock.elapsedRealtime() - base))
                            draw()
                        },
                        IslandAction("Stop") {
                            ctl.setOngoing(null)
                            notice(ctx, "Stopped", fmt(SystemClock.elapsedRealtime() - base), 4000)
                        }
                    ),
                    userInitiated = true
                )
            )
        }
        draw()
    }

    private fun battery(ctx: Context) {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (l < 0 || s <= 0) 0 else l * 100 / s
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        notice(ctx, "Battery", (if (charging) "Charging" else "On battery") + "  ·  " + "%.1f".format(temp) + " °C", 4500, "$pct%")
    }

    private fun history(ctx: Context) {
        val ctl = IslandController.get(ctx)
        ctl.show(
            IslandEvent(
                key = "history", title = "Recent notifications", text = NotifHistory.text(),
                iconRes = android.R.drawable.ic_popup_reminder,
                actions = listOf(IslandAction("Close") { ctl.remove("history") }),
                sticky = true, startExpanded = true, userInitiated = true
            ), 0
        )
    }
}
