package com.digiforgedynamics.island

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper

class BluetoothWatcher(private val ctx: Context) {

    private val h   = Handler(Looper.getMainLooper())
    private val ctl = IslandController.get(ctx)
    private var running = false

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED    -> onConnect(intent)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> onDisconnect(intent)
                ACTION_BT_BATTERY                       -> onBattery(intent)
            }
        }
    }

    fun start() {
        if (running) return; running = true
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BT_BATTERY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(rx, f, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(rx, f)
        }
    }

    fun stop() {
        if (!running) return; running = false
        try { ctx.unregisterReceiver(rx) } catch (_: Exception) {}
    }

    private fun onConnect(intent: Intent) {
        if (!Prefs.btBattery(ctx)) return
        val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val name = dev?.name ?: "Bluetooth device"
        val pct  = btBattery(dev)
        ctl.show(
            IslandEvent(
                key         = "bt_connect",
                title       = name,
                text        = if (pct >= 0) "Connected · $pct%" else "Connected",
                iconRes     = android.R.drawable.stat_sys_data_bluetooth,
                iconTint    = 0xFF3B82F6.toInt(),
                style       = IslandStyle.BT_BATTERY,
                batteryPct  = pct
            ), 4000
        )
    }

    private fun onDisconnect(intent: Intent) {
        val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val name = dev?.name ?: "Bluetooth device"
        ctl.show(
            IslandEvent(
                key     = "bt_disconnect",
                title   = name,
                text    = "Disconnected",
                iconRes = android.R.drawable.stat_sys_data_bluetooth,
                iconTint= 0xFF8E8E98.toInt(),
                style   = IslandStyle.DEFAULT
            ), 3000
        )
    }

    private fun onBattery(intent: Intent) {
        if (!Prefs.btBattery(ctx)) return
        val pct  = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
        val name = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Bluetooth"
        if (pct < 0 || pct > 20) return // only warn on low
        ctl.show(
            IslandEvent(
                key        = "bt_low",
                title      = "$name battery low",
                text       = "$pct% remaining",
                iconRes    = android.R.drawable.stat_sys_data_bluetooth,
                iconTint   = 0xFFFF3B30.toInt(),
                style      = IslandStyle.BT_BATTERY,
                batteryPct = pct
            ), 5000
        )
    }

    @Suppress("MissingPermission")
    private fun btBattery(dev: BluetoothDevice?): Int {
        dev ?: return -1
        return try {
            val m = ctx.getSystemService(BluetoothManager::class.java)
            // Reflect on BluetoothDevice.getBatteryLevel() (hidden API, works on most OEMs)
            val method = dev.javaClass.getMethod("getBatteryLevel")
            (method.invoke(dev) as? Int) ?: -1
        } catch (_: Exception) { -1 }
    }

    companion object {
        private const val ACTION_BT_BATTERY   = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
        private const val EXTRA_DEVICE_NAME   = "android.bluetooth.device.extra.DEVICE_NAME"
    }
}
