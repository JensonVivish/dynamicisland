package com.digiforgedynamics.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build

/** Charging / low-battery events. Lives inside the notification listener, so no foreground service is needed. */
class PowerWatcher(private val ctx: Context) {
    private var running = false
    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { onEvent(i?.action) }
    }

    fun start() {
        if (running) return
        running = true
        val f = IntentFilter()
        f.addAction(Intent.ACTION_POWER_CONNECTED)
        f.addAction(Intent.ACTION_POWER_DISCONNECTED)
        f.addAction(Intent.ACTION_BATTERY_LOW)
        f.addAction(Intent.ACTION_BATTERY_OKAY)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(rx, f, Context.RECEIVER_EXPORTED)
        else ctx.registerReceiver(rx, f)
    }

    fun stop() {
        if (!running) return
        running = false
        try { ctx.unregisterReceiver(rx) } catch (e: Exception) {}
    }

    private fun onEvent(action: String?) {
        if (!Prefs.enabled(ctx) || !Prefs.charging(ctx)) return
        val ctl = IslandController.get(ctx)
        val green = Color.rgb(52, 199, 89)
        val red = Color.rgb(255, 69, 58)
        when (action) {
            Intent.ACTION_POWER_CONNECTED -> ctl.show(
                IslandEvent(key = "charging", title = "Charging",
                    iconRes = android.R.drawable.ic_lock_idle_charging, iconTint = green,
                    accessoryText = pct().toString() + "%", accessoryColor = green), 3500)
            Intent.ACTION_POWER_DISCONNECTED -> ctl.remove("charging")
            Intent.ACTION_BATTERY_LOW -> ctl.show(
                IslandEvent(key = "battery_low", title = "Battery low", text = "Connect a charger soon",
                    iconRes = android.R.drawable.ic_dialog_alert, iconTint = red,
                    accessoryText = pct().toString() + "%", accessoryColor = red, sticky = true), 0)
            Intent.ACTION_BATTERY_OKAY -> ctl.remove("battery_low")
        }
    }

    private fun pct(): Int {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0 || s <= 0) 0 else l * 100 / s
    }
}
