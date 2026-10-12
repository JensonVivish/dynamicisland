package com.digiforgedynamics.island

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class IslandListener : NotificationListenerService() {

    private val seen = HashMap<String, Int>()
    private var sessions: List<MediaController> = emptyList()
    private val watched = ArrayList<Pair<MediaController, MediaController.Callback>>()
    private var msm: MediaSessionManager? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> updateSessions(list) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (Prefs.enabled(this)) {
            try { startForegroundService(Intent(this, KeepAliveService::class.java)) }
            catch (_: Exception) {}
        }
        try {
            val m = getSystemService(MediaSessionManager::class.java)
            msm = m
            val cn = ComponentName(this, IslandListener::class.java)
            m.addOnActiveSessionsChangedListener(sessionsListener, cn)
            updateSessions(m.getActiveSessions(cn))
        } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        clearWatched()
        IslandController.get(this).setMedia(null)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try { handle(sbn) } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        seen.remove(sbn.key)
        IslandController.get(this).remove(sbn.key)
    }

    private fun handle(sbn: StatusBarNotification) {
        if (!Prefs.enabled(this) || !Prefs.notifs(this)) return
        val pkg = sbn.packageName
        if (pkg == packageName || Prefs.isBlocked(this, pkg)) return
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val ex = n.extras
        val cat = n.category
        val isCall = cat == Notification.CATEGORY_CALL
        if (cat == Notification.CATEGORY_TRANSPORT ||
            ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return

        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (ongoing && !isCall) return
        if (!isCall && ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return

        if (!isCall) {
            val r = Ranking()
            if (currentRanking.getRanking(sbn.key, r) &&
                r.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        }

        val pm = packageManager
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        if (title.isNullOrEmpty() && text.isNullOrEmpty()) return

        val sig = ("$title|$text").hashCode()
        if (!isCall && seen[sbn.key] == sig) return
        seen[sbn.key] = sig
        if (seen.size > 300) seen.clear()

        var icon: Drawable? = null
        try { icon = n.getLargeIcon()?.loadDrawable(this) } catch (_: Exception) {}
        if (icon == null) {
            icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }
        }

        val ctl = IslandController.get(this)
        val showChrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val callType = ex.getInt("android.callType", 0)
        val ongoingCall = isCall && (callType == 2 || showChrono)

        val acts = ArrayList<IslandAction>()
        n.actions?.forEach { a ->
            if (acts.size >= 3) return@forEach
            val pi = a.actionIntent ?: return@forEach
            if (!a.remoteInputs.isNullOrEmpty()) return@forEach
            val t = a.title?.toString() ?: return@forEach
            val low = t.lowercase()
            val col = if (isCall) {
                when {
                    low.contains("decline") || low.contains("reject") ||
                    low.contains("hang up") || low.contains("dismiss") -> 0xFFFF3B30.toInt()
                    low.contains("answer") || low.contains("accept")   -> 0xFF34C759.toInt()
                    else -> 0
                }
            } else 0
            acts.add(IslandAction(t, 0, col) { firePending(this, pi); ctl.remove(sbn.key) })
        }

        val chronoBase = if (ongoingCall && showChrono)
            SystemClock.elapsedRealtime() - (System.currentTimeMillis() - n.`when`) else 0L

        ctl.show(
            IslandEvent(
                key = sbn.key,
                title = if (title.isNullOrEmpty()) label else title,
                text = text,
                icon = icon,
                chronoBase = chronoBase,
                actions = acts,
                onOpen = { firePending(this, n.contentIntent) },
                sticky = isCall,
                startExpanded = if (isCall) !ongoingCall else !text.isNullOrEmpty()
            ), Prefs.holdMs(this)
        )
    }

    private fun clearWatched() {
        watched.forEach { (mc, cb) -> try { mc.unregisterCallback(cb) } catch (_: Exception) {} }
        watched.clear()
    }

    private fun updateSessions(list: List<MediaController>?) {
        clearWatched()
        sessions = list ?: emptyList()
        sessions.forEach { mc ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = refreshMedia()
                override fun onMetadataChanged(metadata: MediaMetadata?) = refreshMedia()
                override fun onSessionDestroyed() = refreshMedia()
            }
            try { mc.registerCallback(cb); watched.add(mc to cb) } catch (_: Exception) {}
        }
        refreshMedia()
    }

    private fun refreshMedia() {
        val ctl = IslandController.get(this)
        if (!Prefs.enabled(this) || !Prefs.media(this)) { ctl.setMedia(null); return }
        val mc = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING &&
            !Prefs.isBlocked(this, it.packageName)
        }
        if (mc == null) { ctl.setMedia(null); return }

        val md = mc.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrEmpty()) { ctl.setMedia(null); return }
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)

        var art: Drawable? = null
        try {
            val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            if (bmp != null)
                art = BitmapDrawable(resources,
                    Bitmap.createScaledBitmap(bmp, 96, 96, true))
        } catch (_: Exception) {}
        if (art == null)
            art = try { packageManager.getApplicationIcon(mc.packageName) } catch (_: Exception) { null }

        val tc = mc.transportControls
        ctl.setMedia(
            IslandEvent(
                key = "media:${mc.packageName}|$title",
                title = title,
                text = artist,
                icon = art,
                equalizer = true,
                actions = listOf(
                    IslandAction(null, android.R.drawable.ic_media_previous, 0) { tc.skipToPrevious() },
                    IslandAction(null, android.R.drawable.ic_media_pause,    0) { tc.pause() },
                    IslandAction(null, android.R.drawable.ic_media_next,     0) { tc.skipToNext() }
                ),
                onOpen = { firePending(this, mc.sessionActivity) },
                sticky = true
            )
        )
    }
}
