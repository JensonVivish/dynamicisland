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

// ── Event model ──────────────────────────────────────────────────────────────

enum class IslandStyle {
    DEFAULT,       // standard pill
    CALL,          // pill with waveform
    MEDIA,         // pill with equalizer + album art
    NAV,           // pill with turn icon + distance
    TIMER,         // pill with countdown chronometer
    PROGRESS,      // pill with progress bar (download/transfer)
    PRIVACY,       // privacy dot(s) only
    SPLIT,         // dominant pill + secondary dot
    PAYMENT,       // payment confirmation animation
    FACE_SCAN,     // face-id style expand
    BT_BATTERY,    // Bluetooth accessory battery
    HOTSPOT,       // hotspot/sharing indicator
    LIVE_TILE      // ride-share / food / sports / flight
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
    // accessory
    val accessoryText: String? = null,
    val accessoryColor: Int = -1,
    val chronoBase: Long = 0L,           // for call timer / countdown
    val chronoCountDown: Boolean = false,
    val progressMax: Int = 0,
    val progressVal: Int = 0,
    val batteryPct: Int = -1,            // for BT battery
    val navTurnIcon: Int = 0,            // drawable res for turn arrow
    // secondary dot (split mode)
    val dotColor: Int = 0,
    val dotIconRes: Int = 0,
    // actions
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

// ── Controller ───────────────────────────────────────────────────────────────

class IslandController private constructor(private val app: Context) {

    companion object {
        @Volatile private var inst: IslandController? = null
        fun get(c: Context): IslandController =
            inst ?: synchronized(this) {
                inst ?: IslandController(c.applicationContext).also { inst = it }
            }
    }

    private val wm  = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val h   = Handler(Looper.getMainLooper())

    private var view: IslandView? = null
    private var lp:   WindowManager.LayoutParams? = null

    // event slots
    private var transient: IslandEvent? = null   // highest priority — notifications, calls, etc.
    private var persistent: IslandEvent? = null  // media, nav — stays until cleared
    private var privacyEvt: IslandEvent? = null  // camera/mic dot

    private var expanded       = false
    private var mediaHidden    = false
    private var lastMediaKey   = ""
    private val dismissed      = HashSet<String>()

    private val timeout = Runnable { onTimeout() }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Show a transient event (auto-dismisses after [ms] ms unless sticky). */
    fun show(e: IslandEvent, ms: Long = Prefs.holdMs(app)) {
        h.post {
            if (dismissed.contains(e.key)) return@post
            transient = e
            expanded  = e.startExpanded
            h.removeCallbacks(timeout)
            if (!e.sticky && ms > 0) h.postDelayed(timeout, ms)
            render()
        }
    }

    /** Set a persistent background event (media, navigation). Pass null to clear. */
    fun setPersistent(e: IslandEvent?) {
        h.post {
            if (e == null) {
                if (lastMediaKey.isNotEmpty() && persistent?.key?.startsWith("media:") == true) {
                    mediaHidden = false; lastMediaKey = ""
                }
                persistent = null
            } else {
                if (e.key != lastMediaKey && e.key.startsWith("media:")) {
                    mediaHidden = false; lastMediaKey = e.key
                }
                persistent = e
            }
            render()
        }
    }

    /** Show/hide privacy dot. */
    fun setPrivacy(e: IslandEvent?) {
        h.post { privacyEvt = e; render() }
    }

    fun remove(key: String) {
        h.post {
            dismissed.remove(key)
            if (transient?.key == key) {
                transient = null; expanded = false
                h.removeCallbacks(timeout)
            }
            if (persistent?.key == key) persistent = null
            render()
        }
    }

    fun refresh()       { h.post { render() } }
    fun applySettings() {
        h.post {
            val v = view ?: return@post
            val p = lp   ?: return@post
            applyPos(p)
            try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
        }
    }

    // ── Gesture callbacks (called from IslandView) ────────────────────────────

    fun onTap() {
        val cur = current() ?: return
        if (!expanded && cur.expandable) {
            expanded = true
            h.removeCallbacks(timeout)
            h.postDelayed(timeout, 8000)
            render()
        } else {
            cur.onOpen?.invoke()
            if (cur === transient && !cur.sticky) {
                transient = null; h.removeCallbacks(timeout)
            }
            expanded = false
            render()
        }
    }

    fun onLongPress() {
        val cur = current() ?: return
        if (!expanded) {
            expanded = true
            h.removeCallbacks(timeout)
            h.postDelayed(timeout, 12000) // stay longer on long-press expand
            render()
        }
    }

    fun onSwipeUp() {
        val cur = current() ?: return
        if (expanded) {
            expanded = false
            if (cur === transient && !cur.sticky) {
                h.removeCallbacks(timeout); h.postDelayed(timeout, 1500)
            }
        } else if (cur === transient) {
            if (cur.sticky) dismissed.add(cur.key)
            transient = null; h.removeCallbacks(timeout)
        } else {
            mediaHidden = true // hide media/nav
        }
        render()
    }

    fun onSwipeLeft()  { onSwipeUp() }  // horizontal swipe = dismiss
    fun onSwipeRight() { onSwipeUp() }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun current(): IslandEvent? {
        return transient
            ?: privacyEvt
            ?: if (mediaHidden) null else persistent
    }

    private fun onTimeout() {
        val t = transient
        if (t != null && !t.sticky) transient = null
        expanded = false
        render()
    }

    private fun render() {
        val landscape = app.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        if (!Prefs.enabled(app) || (landscape && Prefs.hideLandscape(app))) {
            expanded = false; detach(); return
        }
        val cur = current()
        if (cur == null) { expanded = false; detach(); return }
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
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Keep above system UI — prevents notifications covering us
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            p.alpha = 1.0f
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
        v.animateOut { try { wm.removeViewImmediate(v) } catch (_: Exception) {} }
    }

    private fun applyPos(p: WindowManager.LayoutParams) {
        val d = app.resources.displayMetrics.density
        p.y = (Prefs.topDp(app) * d).toInt()
        p.x = (Prefs.xDp(app) * d).toInt()
    }
}
