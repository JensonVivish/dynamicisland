package com.digiforgedynamics.island

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

data class IslandAction(
    val label: String?,
    val iconRes: Int,
    val color: Int,
    val onClick: () -> Unit
)

data class IslandEvent(
    val key: String,
    val title: String,
    val text: String? = null,
    val icon: Drawable? = null,
    val iconRes: Int = 0,
    val iconTint: Int = 0,
    val accessoryText: String? = null,
    val accessoryColor: Int = -1,
    val chronoBase: Long = 0L,
    val equalizer: Boolean = false,
    val actions: List<IslandAction> = emptyList(),
    val onOpen: (() -> Unit)? = null,
    val sticky: Boolean = false,
    val startExpanded: Boolean = false
) {
    val expandable: Boolean get() = actions.isNotEmpty() || !text.isNullOrEmpty()
}

fun firePending(ctx: Context, pi: PendingIntent?) {
    if (pi == null) return
    try {
        val opts = ActivityOptions.makeBasic()
        pi.send(ctx, 0, null, null, null, null, opts.toBundle())
    } catch (_: Exception) {}
}

class IslandController private constructor(private val app: Context) {

    companion object {
        @Volatile private var inst: IslandController? = null
        fun get(c: Context): IslandController =
            inst ?: synchronized(this) {
                inst ?: IslandController(c.applicationContext).also { inst = it }
            }
    }

    private val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val h = Handler(Looper.getMainLooper())

    private var view: IslandView? = null
    private var lp: WindowManager.LayoutParams? = null

    private var transient: IslandEvent? = null
    private var media: IslandEvent? = null
    private var mediaHidden = false
    private var lastMediaKey = ""
    private var expanded = false
    private val dismissed = HashSet<String>()

    private val timeout = Runnable { onTimeout() }

    fun show(e: IslandEvent, ms: Long) {
        h.post {
            if (dismissed.contains(e.key)) return@post
            transient = e
            expanded = e.startExpanded
            h.removeCallbacks(timeout)
            if (!e.sticky && ms > 0) h.postDelayed(timeout, ms)
            render()
        }
    }

    fun remove(key: String) {
        h.post {
            dismissed.remove(key)
            if (transient?.key == key) {
                transient = null
                expanded = false
                h.removeCallbacks(timeout)
                render()
            }
        }
    }

    fun setMedia(e: IslandEvent?) {
        h.post {
            if (e == null) {
                media = null; lastMediaKey = ""; mediaHidden = false
            } else {
                if (e.key != lastMediaKey) { mediaHidden = false; lastMediaKey = e.key }
                media = e
            }
            render()
        }
    }

    fun refresh() { h.post { render() } }

    fun applySettings() {
        h.post {
            val v = view ?: return@post
            val p = lp ?: return@post
            applyPos(p)
            try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
        }
    }

    fun onTap() {
        val cur = current() ?: return
        if (!expanded && cur.expandable) {
            expanded = true
            h.removeCallbacks(timeout)
            h.postDelayed(timeout, 7000)
            render()
        } else {
            onOpen()
        }
    }

    fun onOpen() {
        val cur = current() ?: return
        cur.onOpen?.invoke()
        if (cur === transient && !cur.sticky) {
            transient = null
            h.removeCallbacks(timeout)
        }
        expanded = false
        render()
    }

    fun onSwipeUp() {
        val cur = current() ?: return
        if (expanded) {
            expanded = false
            if (cur === transient && !cur.sticky) {
                h.removeCallbacks(timeout)
                h.postDelayed(timeout, 1200)
            }
        } else if (cur === transient) {
            if (cur.sticky) dismissed.add(cur.key)
            transient = null
            h.removeCallbacks(timeout)
        } else {
            mediaHidden = true
        }
        render()
    }

    private fun current(): IslandEvent? = transient ?: (if (mediaHidden) null else media)

    private fun onTimeout() {
        val t = transient
        if (t != null && !t.sticky) transient = null
        expanded = false
        render()
    }

    private fun render() {
        val cur = current()
        val landscape =
            app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (cur == null || !Prefs.enabled(app) || (landscape && Prefs.hideLandscape(app))) {
            expanded = false; detach(); return
        }
        val v = view ?: attach() ?: return
        v.bind(cur, expanded)
    }

    private fun attach(): IslandView? {
        if (!Settings.canDrawOverlays(app)) return null
        val v = IslandView(app, this)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        p.title = "DigiIsland"
        applyPos(p)
        return try {
            wm.addView(v, p)
            view = v; lp = p
            v.animateIn()
            v
        } catch (_: Exception) { null }
    }

    private fun detach() {
        val v = view ?: return
        view = null; lp = null
        v.animateOut {
            try { wm.removeViewImmediate(v) } catch (_: Exception) {}
        }
    }

    private fun applyPos(p: WindowManager.LayoutParams) {
        val d = app.resources.displayMetrics.density
        p.y = (Prefs.topDp(app) * d).toInt()
        p.x = (Prefs.xDp(app) * d).toInt()
    }
}
