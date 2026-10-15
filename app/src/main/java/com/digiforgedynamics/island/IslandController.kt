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

enum class IslandStyle {
    DEFAULT, CALL, MEDIA, NAV, TIMER, PROGRESS, PRIVACY, SPLIT, PAYMENT,
    FACE_SCAN, BT_BATTERY, HOTSPOT, LIVE_TILE, IDLE, MENU
}

data class IslandAction(
    val label: String?,
    val iconRes: Int = 0,
    val color: Int = 0,
    val onClick: () -> Unit
)

data class IslandEvent(
    val key: String,
    val title: String,
    val text: String? = null,
    val subText: String? = null,
    val icon: Drawable? = null,
    val iconRes: Int = 0,
    val iconTint: Int = 0,
    val style: IslandStyle = IslandStyle.DEFAULT,
    val accessoryText: String? = null,
    val accessoryColor: Int = -1,
    val chronoBase: Long = 0L,
    val chronoCountDown: Boolean = false,
    val progressMax: Int = 0,
    val progressVal: Int = 0,
    val batteryPct: Int = -1,
    val navTurnIcon: Int = 0,
    val dotColor: Int = 0,
    val dotIconRes: Int = 0,
    val actions: List<IslandAction> = emptyList(),
    val onOpen: (() -> Unit)? = null,
    val onReply: ((String) -> Unit)? = null,
    val sticky: Boolean = false,
    val startExpanded: Boolean = false,
    val userInitiated: Boolean = false
) {
    val expandable: Boolean
        get() = actions.isNotEmpty() || !text.isNullOrEmpty() || onReply != null
}

fun firePending(ctx: Context, pi: PendingIntent?) {
    if (pi == null) return
    try {
        val opts = ActivityOptions.makeBasic()
        pi.send(ctx, 0, null, null, null, null, opts.toBundle())
    } catch (e: Exception) {
    }
}

class IslandController private constructor(private val app: Context) {

    companion object {
        @Volatile
        private var inst: IslandController? = null
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
    private var ongoing: IslandEvent? = null     // timer / stopwatch
    private var persistent: IslandEvent? = null  // media
    private var privacyEvt: IslandEvent? = null
    private val idleEvt = IslandEvent(key = "idle", title = "", style = IslandStyle.IDLE)

    private var expanded = false
    private var mediaHidden = false
    private var lastMediaKey = ""
    private var lastIdle = true
    private val dismissed = HashSet<String>()

    var typing = false
        private set

    private val timeout = Runnable { onTimeout() }

    // ---- public API ---------------------------------------------------------------------

    fun show(e: IslandEvent, ms: Long = Prefs.holdMs(app)) {
        h.post {
            if (!e.userInitiated && dismissed.contains(e.key)) return@post
            val same = transient?.key == e.key
            if (!same && typing) endTypingInternal()
            transient = e
            if (!same) expanded = e.startExpanded
            h.removeCallbacks(timeout)
            if (!e.sticky && ms > 0 && !typing) h.postDelayed(timeout, ms)
            render()
        }
    }

    fun setOngoing(e: IslandEvent?) {
        h.post { ongoing = e; render() }
    }

    fun setPersistent(e: IslandEvent?) {
        h.post {
            if (e == null) {
                persistent = null
                mediaHidden = false
                lastMediaKey = ""
            } else {
                if (e.key != lastMediaKey && e.key.startsWith("media:")) {
                    mediaHidden = false
                    lastMediaKey = e.key
                }
                persistent = e
            }
            render()
        }
    }

    fun setPrivacy(e: IslandEvent?) {
        h.post { privacyEvt = e; render() }
    }

    fun remove(key: String) {
        h.post {
            dismissed.remove(key)
            if (transient?.key == key) {
                if (typing) endTypingInternal()
                transient = null
                expanded = false
                h.removeCallbacks(timeout)
            }
            if (ongoing?.key == key) ongoing = null
            if (persistent?.key == key) persistent = null
            render()
        }
    }

    fun refresh() { h.post { render() } }

    fun applySettings() {
        h.post {
            val v = view
            val p = lp
            if (v != null && p != null) {
                applyPos(p, lastIdle)
                try { wm.updateViewLayout(v, p) } catch (e: Exception) {}
            }
            render()
        }
    }

    // ---- typing (reply box) -------------------------------------------------------------

    fun beginTyping() {
        h.post {
            if (typing || transient == null) return@post
            typing = true
            setFocusable(true)
            h.removeCallbacks(timeout)
            render()
        }
    }

    fun endTyping() {
        h.post {
            endTypingInternal()
            render()
        }
    }

    fun sendReply(text: String) {
        h.post {
            val cur = transient
            endTypingInternal()
            cur?.onReply?.invoke(text)
            if (cur != null) {
                transient = null
                expanded = false
            }
            h.removeCallbacks(timeout)
            render()
        }
    }

    private fun endTypingInternal() {
        if (!typing) return
        typing = false
        view?.hideKeyboard()
        setFocusable(false)
        h.removeCallbacks(timeout)
        val t = transient
        if (t != null && !t.sticky) h.postDelayed(timeout, 4000)
    }

    private fun setFocusable(on: Boolean) {
        val v = view ?: return
        val p = lp ?: return
        if (on) {
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        } else {
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }
        try { wm.updateViewLayout(v, p) } catch (e: Exception) {}
    }

    // ---- gestures -----------------------------------------------------------------------

    fun onTap() {
        if (typing) return
        val cur = current()
        if (cur == null) {
            QuickMenu.open(app)
            return
        }
        if (cur.style == IslandStyle.MENU) return
        if (!expanded && cur.expandable) {
            expanded = true
            h.removeCallbacks(timeout)
            h.postDelayed(timeout, 8000)
            render()
        } else {
            cur.onOpen?.invoke()
            if (cur === transient && !cur.sticky) {
                transient = null
                h.removeCallbacks(timeout)
            }
            expanded = false
            render()
        }
    }

    fun onLongPress() {
        if (typing) return
        val cur = current()
        if (cur == null) {
            QuickMenu.open(app)
            return
        }
        if (!expanded && cur.expandable) {
            expanded = true
            h.removeCallbacks(timeout)
            h.postDelayed(timeout, 12000)
            render()
        }
    }

    fun onSwipeUp() {
        if (typing) {
            endTypingInternal()
            render()
            return
        }
        val cur = current() ?: return
        if (cur.style == IslandStyle.MENU) {
            transient = null
            expanded = false
            h.removeCallbacks(timeout)
        } else if (expanded) {
            expanded = false
            if (cur === transient && !cur.sticky) {
                h.removeCallbacks(timeout)
                h.postDelayed(timeout, 1500)
            }
        } else if (cur === transient) {
            if (cur.sticky && !cur.userInitiated) dismissed.add(cur.key)
            transient = null
            h.removeCallbacks(timeout)
        } else if (cur === ongoing) {
            ongoing = null
        } else {
            mediaHidden = true
        }
        render()
    }

    fun onSwipeLeft() { onSwipeUp() }
    fun onSwipeRight() { onSwipeUp() }

    // ---- internals ----------------------------------------------------------------------

    private fun current(): IslandEvent? =
        transient ?: ongoing ?: privacyEvt ?: (if (mediaHidden) null else persistent)

    private fun onTimeout() {
        val t = transient
        if (t != null && !t.sticky) transient = null
        expanded = false
        render()
    }

    private fun render() {
        val landscape =
            app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val cur = current() ?: (if (Prefs.idle(app)) idleEvt else null)
        if (cur == null || !Prefs.enabled(app) || (landscape && Prefs.hideLandscape(app))) {
            expanded = false
            detach()
            return
        }
        val idle = cur.style == IslandStyle.IDLE
        val v = view ?: attach(idle) ?: return
        if (idle != lastIdle) {
            lastIdle = idle
            val p = lp
            if (p != null) {
                applyPos(p, idle)
                try { wm.updateViewLayout(v, p) } catch (e: Exception) {}
            }
        }
        v.bind(cur, if (idle) false else expanded)
    }

    private fun attach(idle: Boolean): IslandView? {
        if (!Settings.canDrawOverlays(app)) return null
        val v = IslandView(app, this)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        p.title = "DigiIsland"
        lastIdle = idle
        applyPos(p, idle)
        return try {
            wm.addView(v, p)
            view = v
            lp = p
            v.animateIn()
            v
        } catch (e: Exception) {
            null
        }
    }

    private fun detach() {
        val v = view ?: return
        view = null
        lp = null
        typing = false
        v.animateOut {
            try { wm.removeViewImmediate(v) } catch (e: Exception) {}
        }
    }

    private fun applyPos(p: WindowManager.LayoutParams, idle: Boolean) {
        val d = app.resources.displayMetrics.density
        val top = Prefs.topDp(app) + (if (idle) 0 else Prefs.expDrop(app))
        p.y = (top * d).toInt()
        p.x = (Prefs.xDp(app) * d).toInt()
    }
}
