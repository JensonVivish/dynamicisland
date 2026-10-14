package com.digiforgedynamics.island

import android.content.Context
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper

/**
 * Camera / microphone dots using only public APIs and no special permissions:
 * CameraManager availability callbacks and AudioManager recording callbacks.
 * No scanning of installed apps.
 */
class PrivacyWatcher(private val ctx: Context) {
    private val h = Handler(Looper.getMainLooper())
    private val ctl = IslandController.get(ctx)
    private val cm = ctx.getSystemService(CameraManager::class.java)
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val busyCams = HashSet<String>()
    private var mic = false
    private var running = false

    private val camCb = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) { busyCams.add(cameraId); refresh() }
        override fun onCameraAvailable(cameraId: String) { busyCams.remove(cameraId); refresh() }
    }
    private val micCb = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            mic = !configs.isNullOrEmpty(); refresh()
        }
    }

    fun start() {
        if (running) return
        running = true
        try { cm.registerAvailabilityCallback(camCb, h) } catch (e: Exception) {}
        try { am.registerAudioRecordingCallback(micCb, h) } catch (e: Exception) {}
    }

    fun stop() {
        running = false
        try { cm.unregisterAvailabilityCallback(camCb) } catch (e: Exception) {}
        try { am.unregisterAudioRecordingCallback(micCb) } catch (e: Exception) {}
        busyCams.clear(); mic = false
        ctl.setPrivacy(null)
    }

    private fun refresh() {
        val cam = busyCams.isNotEmpty()
        if (!running || !Prefs.enabled(ctx) || !Prefs.privacyDots(ctx) || (!cam && !mic)) {
            ctl.setPrivacy(null); return
        }
        val col = if (mic) Color.rgb(255, 159, 10) else Color.rgb(48, 209, 88)
        val label = if (cam && mic) "Camera & Mic in use" else if (cam) "Camera in use" else "Microphone in use"
        ctl.setPrivacy(IslandEvent(
            key = "privacy", title = label,
            iconRes = if (cam) android.R.drawable.ic_menu_camera else android.R.drawable.ic_btn_speak_now,
            iconTint = col, style = IslandStyle.PRIVACY, dotColor = col, sticky = true))
    }
}
