package com.digiforgedynamics.island

import android.app.AppOpsManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper

class PrivacyWatcher(private val ctx: Context) {

    private val aom = ctx.getSystemService(AppOpsManager::class.java)
    private val h   = Handler(Looper.getMainLooper())
    private val ctl = IslandController.get(ctx)
    private var running = false

    private val poll = object : Runnable {
        override fun run() { if (!running) return; check(); h.postDelayed(this, 1500) }
    }

    fun start() { if (running) return; running = true; h.post(poll) }
    fun stop()  { running = false; h.removeCallbacks(poll); ctl.setPrivacy(null) }

    private fun check() {
        if (!Prefs.enabled(ctx) || !Prefs.privacyDots(ctx)) { ctl.setPrivacy(null); return }
        val cam = isOpActive(AppOpsManager.OPSTR_CAMERA)
        val mic = isOpActive(AppOpsManager.OPSTR_RECORD_AUDIO)
        if (!cam && !mic) { ctl.setPrivacy(null); return }

        val green  = Color.rgb(48, 209, 88)
        val orange = Color.rgb(255, 159, 10)
        val col    = if (mic) orange else green
        val label  = when {
            cam && mic -> "Camera & Mic in use"
            cam        -> "Camera in use"
            else       -> "Microphone in use"
        }
        ctl.setPrivacy(
            IslandEvent(
                key     = "privacy",
                title   = label,
                iconRes = if (cam) android.R.drawable.ic_menu_camera
                          else     android.R.drawable.ic_btn_speak_now,
                iconTint= col,
                style   = IslandStyle.PRIVACY,
                dotColor= col,
                sticky  = true
            )
        )
    }

    private fun isOpActive(op: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Q+ we can check which packages are actively using the op
                aom.getPackagesForOps(arrayOf(op))?.any { entry ->
                    entry.ops?.any { it.op == AppOpsManager.opToSwitch(op) &&
                        (it.isRunning) } == true
                } == true
            } else {
                false
            }
        } catch (_: Exception) { false }
    }
}
