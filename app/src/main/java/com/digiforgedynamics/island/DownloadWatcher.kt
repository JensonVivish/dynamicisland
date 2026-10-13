package com.digiforgedynamics.island

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Tracks active downloads and shows a live progress ring on the island. */
class DownloadWatcher(private val ctx: Context) {

    private val h   = Handler(Looper.getMainLooper())
    private val ctl = IslandController.get(ctx)
    private val dm  = ctx.getSystemService(DownloadManager::class.java)
    private var running = false
    private var activeId = -1L

    private val observer = object : ContentObserver(h) {
        override fun onChange(self: Boolean) { poll() }
    }

    private val completeRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val id = i?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id == activeId) { ctl.remove("download"); activeId = -1L }
        }
    }

    fun start() {
        if (running) return; running = true
        ctx.contentResolver.registerContentObserver(
            Uri.parse("content://downloads/my_downloads"), true, observer)
        val f = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ctx.registerReceiver(completeRx, f, Context.RECEIVER_EXPORTED)
        else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(completeRx, f)
        }
    }

    fun stop() {
        if (!running) return; running = false
        try { ctx.contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
        try { ctx.unregisterReceiver(completeRx) } catch (_: Exception) {}
        ctl.remove("download")
    }

    private fun poll() {
        try {
            val q = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING)
            val c = dm.query(q) ?: return
            if (!c.moveToFirst()) { c.close(); ctl.remove("download"); return }
            val idCol    = c.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val doneCol  = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totCol   = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val id       = if (idCol >= 0) c.getLong(idCol) else -1L
            val title    = if (titleCol >= 0) c.getString(titleCol) else "Downloading"
            val done     = if (doneCol >= 0) c.getLong(doneCol) else 0L
            val total    = if (totCol  >= 0) c.getLong(totCol)  else 0L
            c.close()
            activeId = id
            val pct = if (total > 0) (done * 100 / total).toInt() else 0
            ctl.show(
                IslandEvent(
                    key         = "download",
                    title       = title.ifBlank { "Downloading" },
                    text        = if (total > 0) formatBytes(done) + " / " + formatBytes(total) else null,
                    iconRes     = android.R.drawable.stat_sys_download,
                    iconTint    = 0xFF34C759.toInt(),
                    style       = IslandStyle.PROGRESS,
                    progressMax = 100,
                    progressVal = pct,
                    sticky      = true
                ), 0
            )
        } catch (_: Exception) {}
    }

    private fun formatBytes(b: Long) = when {
        b >= 1_000_000L -> "%.1f MB".format(b / 1_000_000.0)
        b >= 1_000L     -> "%.0f KB".format(b / 1_000.0)
        else            -> "$b B"
    }
}
