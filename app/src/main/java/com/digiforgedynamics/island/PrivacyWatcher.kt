package com.digiforgedynamics.island

import android.app.AppOpsManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Polls every 1.5 s to detect any app actively using the camera or microphone.
 * Uses the public AppOpsManager.checkOp / noteOp path — no hidden APIs.
 * Android 10+ supports AppOpsManager.OnOpActiveChangedListener for instant notification;
 * we register that as well so the dot appears immediately, not just on the next poll.
 */
class PrivacyWatcher(private val ctx: Context) {

    private val aom = ctx.getSystemService(AppOpsManager::class.java)
    private val h   = Handler(Looper.getMainLooper())
    private val ctl = IslandController.get(ctx)
    private var running = false

    // Active-changed listeners (API 28+)
    private var camListener: AppOpsManager.OnOpActiveChangedListener? = null
    private var micListener: AppOpsManager.OnOpActiveChangedListener? = null

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            refresh()
            h.postDelayed(this, 1500)
        }
    }

    fun start() {
        if (running) return
        running = true

        // Register instant-callback listeners on API 28+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                camListener = AppOpsManager.OnOpActiveChangedListener { _, _, _, _ -> refresh() }
                micListener = AppOpsManager.OnOpActiveChangedListener { _, _, _, _ -> refresh() }
                aom.startWatchingActive(arrayOf(AppOpsManager.OPSTR_CAMERA),
                    ctx.mainExecutor, camListener!!)
                aom.startWatchingActive(arrayOf(AppOpsManager.OPSTR_RECORD_AUDIO),
                    ctx.mainExecutor, micListener!!)
            } catch (_: Exception) {
                camListener = null; micListener = null
            }
        }

        h.post(poll)
    }

    fun stop() {
        running = false
        h.removeCallbacks(poll)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { camListener?.let { aom.stopWatchingActive(it) } } catch (_: Exception) {}
            try { micListener?.let { aom.stopWatchingActive(it) } } catch (_: Exception) {}
        }
        camListener = null; micListener = null
        ctl.setPrivacy(null)
    }

    private fun refresh() {
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
                iconTint = col,
                style    = IslandStyle.PRIVACY,
                dotColor = col,
                sticky   = true
            )
        )
    }

    /**
     * Check whether the op is currently active for ANY package.
     * Uses isOpActive (API 29+) which is the correct public way to detect ongoing ops.
     * Falls back to MODE_ALLOWED check on older APIs (less accurate but safe).
     */
    private fun isOpActive(op: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try { aom.isOpActive(op, android.os.Process.myUid(), ctx.packageName)
                         // The above only checks our own process — instead scan installed apps
                         || installedUids().any { (pkg, uid) ->
                                try { aom.isOpActive(op, uid, pkg) }
                                catch (_: Exception) { false }
                            }
            } catch (_: Exception) { false }
        }
        // API 28 and below: just check if the op mode is allowed (not perfect but lightweight)
        return try {
            aom.checkOpNoThrow(op, android.os.Process.myUid(), ctx.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun installedUids(): List<Pair<String, Int>> {
        return try {
            ctx.packageManager.getInstalledPackages(0).map {
                it.packageName to it.applicationInfo.uid
            }
        } catch (_: Exception) { emptyList() }
    }
}
