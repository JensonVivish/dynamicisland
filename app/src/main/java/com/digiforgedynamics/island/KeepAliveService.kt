package com.digiforgedynamics.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder

class KeepAliveService : Service() {

    private var rx: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Island service", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_island)
            .setContentTitle("DigiIsland is running")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = onPower(intent?.action)
        }
        rx = r
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(r, f, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(r, f)
        }
        IslandController.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        IslandController.get(this).refresh()
    }

    override fun onDestroy() {
        rx?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        rx = null
        IslandController.get(this).refresh()
        super.onDestroy()
    }

    private fun onPower(action: String?) {
        if (!Prefs.enabled(this) || !Prefs.charging(this)) return
        val ctl = IslandController.get(this)
        val green = Color.rgb(52, 199, 89)
        val red   = Color.rgb(255, 69, 58)
        when (action) {
            Intent.ACTION_POWER_CONNECTED -> ctl.show(
                IslandEvent(
                    key = "charging",
                    title = "Charging",
                    iconRes = android.R.drawable.ic_lock_idle_charging,
                    iconTint = green,
                    accessoryText = "${pct()}%",
                    accessoryColor = green
                ), 3500
            )
            Intent.ACTION_POWER_DISCONNECTED -> ctl.remove("charging")
            Intent.ACTION_BATTERY_LOW -> ctl.show(
                IslandEvent(
                    key = "battery_low",
                    title = "Battery low",
                    iconRes = android.R.drawable.ic_dialog_alert,
                    iconTint = red,
                    accessoryText = "${pct()}%",
                    accessoryColor = red
                ), 5000
            )
        }
    }

    private fun pct(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0 || s <= 0) 0 else l * 100 / s
    }

    companion object { private const val CH = "island_service" }
}
