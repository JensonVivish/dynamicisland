package com.digiforgedynamics.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val c = context ?: return
        if (Prefs.enabled(c) && Prefs.keepAlive(c) && Settings.canDrawOverlays(c)) {
            try {
                c.startForegroundService(Intent(c, KeepAliveService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
