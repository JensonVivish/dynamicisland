package com.digiforgedynamics.island

import android.animation.*
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.InputMethodManager
import android.view.*
import android.view.animation.*
import android.widget.*
import kotlin.math.*

// ── Animated equalizer bars (music) ──────────────────────────────────────────
class EqView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(34, 211, 238) }
    private var t = 0f
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { t += 0.35f; invalidate(); h.postDelayed(this, 90) }
    }
    override fun onAttachedToWindow()   { super.onAttachedToWindow();    h.post(tick) }
    override fun onDetachedFromWindow() { h.removeCallbacks(tick); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val bw = w / 5f
        for (i in 0..2) {
            val amp = 0.3f + 0.7f * abs(sin(t + i * 1.7f))
            val bh  = h * amp
            c.drawRoundRect(i * 2 * bw, h - bh, i * 2 * bw + bw, h, bw/2, bw/2, p)
        }
    }
}

// ── Mic / call waveform ───────────────────────────────────────────────────────
class WaveView(ctx: Context, col: Int = Color.rgb(52, 199, 89)) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
    private var t = 0f
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { t += 0.28f; invalidate(); h.postDelayed(this, 60) }
    }
    override fun onAttachedToWindow()   { super.onAttachedToWindow();    h.post(tick) }
    override fun onDetachedFromWindow() { h.removeCallbacks(tick); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val ht = height.toFloat()
        val bars = 5; val gap = w / (bars * 2 - 1f); val bw = gap * 0.75f
        for (i in 0 until bars) {
            val amp = 0.2f + 0.8f * abs(sin(t + i * 1.3f))
            val bh  = max(ht * 0.15f, ht * amp)
            val x   = i * 2 * gap
            c.drawRoundRect(x, ht - bh, x + bw, ht, bw/2, bw/2, p)
        }
    }
}

// ── Circular progress ring (downloads) ───────────────────────────────────────
class RingProgress(ctx: Context) : View(ctx) {
    private val bg   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x33FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(34,211,238); strokeCap = Paint.Cap.ROUND }
    var pct = 0f; set(v) { field = v; invalidate() }
    override fun onDraw(c: Canvas) {
        val cx = width/2f; val cy = height/2f; val r = min(cx,cy) - 3f
        val rect = RectF(cx-r, cy-r, cx+r, cy+r)
        c.drawOval(rect, bg); c.drawArc(rect, -90f, 360f * pct, false, fill)
    }
}

// ── Pulsing dot (privacy indicator) ──────────────────────────────────────────
class PulseDot(ctx: Context, col: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
    private var sc = 1f
    private val anim = ValueAnimator.ofFloat(0.6f, 1.3f, 0.6f).apply {
        duration = 1100; repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { sc = it.animatedValue as Float; invalidate() }
    }
    override fun onAttachedToWindow()   { super.onAttachedToWindow();    anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        c.drawCircle(width/2f, height/2f, min(width,height)/2f * sc, p)
    }
}

// ── Face / payment scan ring ──────────────────────────────────────────────────
class ScanRing(ctx: Context) : View(ctx) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = 0x44FFFFFF }
    private val arc  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = Color.rgb(34,211,238); strokeCap = Paint.Cap.ROUND }
    private var sweep = 0f
    private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1400; repeatCount = 3; interpolator = LinearInterpolator()
        addUpdateListener { sweep = it.animatedValue as Float; invalidate() }
    }
    override fun onAttachedToWindow()   { super.onAttachedToWindow();    anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val cx = width/2f; val cy = height/2f; val r = min(cx,cy) - 3f
        val rect = RectF(cx-r, cy-r, cx+r, cy+r)
        c.drawOval(rect, ring); c.drawArc(rect, -90f, sweep, false, arc)
    }
}

// ── Main island pill ─────────────────────────────────────────────────────────
class IslandView(ctx: Context, private val c: IslandController) : FrameLayout(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Number) = (v.toFloat() * d + 0.5f).toInt()

    // Container
    private val pill    = LinearLayout(ctx)
    private val pillBg  = GradientDrawable()

    // Left icon
    private val icon    = ImageView(ctx)

    // Centre column  ← all spacing lives here
    private val colView = LinearLayout(ctx)
    private val titleTv = TextView(ctx)
    private val bodyTv  = TextView(ctx)
    private val subTv   = TextView(ctx)
    private val chron   = Chronometer(ctx)
    private val pBarH   = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
    private val navRow  = LinearLayout(ctx)
    private val navIcon = ImageView(ctx)
    private val navDist = TextView(ctx)

    // Right accessories
    private val eqView   = EqView(ctx)
    private val waveView = WaveView(ctx, Color.rgb(52,199,89))
    private val rProg    = RingProgress(ctx)
    private val scanRing = ScanRing(ctx)
    private val accTv    = TextView(ctx)
    private val dot      = PulseDot(ctx, Color.rgb(34,211,238))

    // Action row
    private val actRow = LinearLayout(ctx)
    private val hRow = LinearLayout(ctx)
    private val menuBox = LinearLayout(ctx)
    private val replyBox = LinearLayout(ctx)
    private val replyEt = EditText(ctx)
    private var wasTyping = false

    // Size anim
    private var sizeAnim: ValueAnimator? = null
    private var shown       = false
    private var curExpanded = false

    // Gesture
    private var downX = 0f; private var downY = 0f
    private var downT = 0L; private var isLong = false
    private val longCb = Runnable { isLong = true; c.onLongPress() }

    init {
        // ── pill background ──
        pillBg.setColor(Color.BLACK); pillBg.cornerRadius = dp(18).toFloat()
        pill.background = pillBg
        pill.orientation = LinearLayout.VERTICAL
        pill.clipToOutline = true
        pill.minimumWidth = dp(90)
        // outer padding of the pill: 12 left/right, 9 top/bottom — gives breathing room
        pill.setPadding(dp(12), dp(9), dp(12), dp(9))

        // ── icon ──
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        icon.clipToOutline = true
        icon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, v.width * 0.28f)
            }
        }

        // ── title / body / sub ──
        titleTv.setTextColor(Color.WHITE)
        titleTv.textSize = 13.5f
        titleTv.setTypeface(titleTv.typeface, android.graphics.Typeface.BOLD)
        titleTv.maxLines = 1; titleTv.ellipsize = TextUtils.TruncateAt.END

        bodyTv.setTextColor(0xFFB2B2BA.toInt())
        bodyTv.textSize = 12f; bodyTv.maxLines = 2; bodyTv.ellipsize = TextUtils.TruncateAt.END
        // 3 dp top margin so title and body never crowd each other
        (LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(3) })
            .let { bodyTv.layoutParams = it }

        subTv.setTextColor(0xFF8E8E98.toInt())
        subTv.textSize = 11f; subTv.maxLines = 1; subTv.ellipsize = TextUtils.TruncateAt.END
        (LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(2) })
            .let { subTv.layoutParams = it }

        chron.setTextColor(Color.rgb(52,199,89)); chron.textSize = 13f

        // ── progress bar ──
        pBarH.max = 100
        pBarH.progressDrawable?.setColorFilter(Color.rgb(34,211,238),
            android.graphics.PorterDuff.Mode.SRC_IN)

        // ── nav row ──
        navRow.orientation = LinearLayout.HORIZONTAL; navRow.gravity = Gravity.CENTER_VERTICAL
        navIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        navDist.setTextColor(Color.WHITE); navDist.textSize = 13f
        navDist.setTypeface(navDist.typeface, android.graphics.Typeface.BOLD)
        navRow.addView(navIcon, LinearLayout.LayoutParams(dp(20), dp(20)))
        val ndLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginStart = dp(5) }
        navRow.addView(navDist, ndLp)

        // ── centre column ──
        colView.orientation = LinearLayout.VERTICAL
        colView.addView(titleTv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        colView.addView(bodyTv)
        colView.addView(subTv)
        colView.addView(chron, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(2) })
        colView.addView(pBarH, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).also { it.topMargin = dp(4) })
        colView.addView(navRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(2) })

        // ── right accessories ──
        accTv.setTextColor(Color.WHITE); accTv.textSize = 13f
        accTv.setTypeface(accTv.typeface, android.graphics.Typeface.BOLD)

        // ── action row ──
        actRow.orientation = LinearLayout.HORIZONTAL; actRow.gravity = Gravity.CENTER

        // ── header row: icon | col | accessories ──
        hRow.orientation = LinearLayout.HORIZONTAL; hRow.gravity = Gravity.CENTER_VERTICAL

        // icon box — 30 dp square so it never touches the title
        val iconLp = LinearLayout.LayoutParams(dp(30), dp(30))
        hRow.addView(icon, iconLp)

        // centre col with 10 dp margins left/right for clear separation
        val colLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        colLp.marginStart = dp(10); colLp.marginEnd = dp(8)
        hRow.addView(colView, colLp)

        // right accessories stacked in a tiny FrameLayout
        val rightBox = FrameLayout(ctx)
        val accSize  = dp(28)
        rightBox.addView(eqView,   FrameLayout.LayoutParams(dp(22), dp(16)).also { it.gravity = Gravity.CENTER })
        rightBox.addView(waveView, FrameLayout.LayoutParams(dp(28), dp(18)).also { it.gravity = Gravity.CENTER })
        rightBox.addView(rProg,    FrameLayout.LayoutParams(dp(26), dp(26)).also { it.gravity = Gravity.CENTER })
        rightBox.addView(scanRing, FrameLayout.LayoutParams(dp(30), dp(30)).also { it.gravity = Gravity.CENTER })
        rightBox.addView(accTv,    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER })
        rightBox.addView(dot,      FrameLayout.LayoutParams(dp(10), dp(10)).also { it.gravity = Gravity.CENTER })
        hRow.addView(rightBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))

        // ── assemble pill ──
        pill.addView(hRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val actLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        actLp.topMargin = dp(10)
        pill.addView(actRow, actLp)

        // quick-menu grid
        menuBox.orientation = LinearLayout.VERTICAL
        menuBox.visibility = View.GONE
        pill.addView(menuBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10) })

        // reply box: quick replies + text field + send
        replyBox.orientation = LinearLayout.VERTICAL
        replyBox.visibility = View.GONE
        val chips = LinearLayout(ctx)
        chips.orientation = LinearLayout.HORIZONTAL
        for (q in listOf("\uD83D\uDC4D", "OK", "On my way", "Call you later")) {
            val t = TextView(ctx)
            t.text = q; t.setTextColor(Color.WHITE); t.textSize = 12.5f
            t.setPadding(dp(10), dp(6), dp(10), dp(6))
            val cg = GradientDrawable(); cg.cornerRadius = dp(14).toFloat(); cg.setColor(0xFF2C2C2E.toInt())
            t.background = cg
            t.setOnClickListener { c.sendReply(q) }
            chips.addView(t, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(6) })
        }
        val chipScroll = HorizontalScrollView(ctx)
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(chips)
        replyBox.addView(chipScroll)
        val inRow = LinearLayout(ctx)
        inRow.orientation = LinearLayout.HORIZONTAL
        inRow.gravity = Gravity.CENTER_VERTICAL
        replyEt.hint = "Type a reply\u2026"
        replyEt.setHintTextColor(0xFF8E8E98.toInt()); replyEt.setTextColor(Color.WHITE); replyEt.textSize = 14f
        replyEt.maxLines = 3
        replyEt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        replyEt.setPadding(dp(12), dp(8), dp(12), dp(8))
        val eg = GradientDrawable(); eg.cornerRadius = dp(18).toFloat(); eg.setColor(0xFF1F1F24.toInt())
        replyEt.background = eg
        val send = TextView(ctx)
        send.text = "Send"; send.setTextColor(Color.BLACK); send.textSize = 13.5f; send.gravity = Gravity.CENTER
        send.setTypeface(send.typeface, android.graphics.Typeface.BOLD)
        send.setPadding(dp(14), 0, dp(14), 0)
        val sg = GradientDrawable(); sg.cornerRadius = dp(18).toFloat(); sg.setColor(0xFF22D3EE.toInt())
        send.background = sg
        send.setOnClickListener {
            val t = replyEt.text.toString().trim()
            if (t.isNotEmpty()) { replyEt.setText(""); c.sendReply(t) }
        }
        inRow.addView(replyEt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inRow.addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).also { it.marginStart = dp(8) })
        replyBox.addView(inRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
        pill.addView(replyBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10) })

        // ── touch ──
        pill.setOnTouchListener { _, ev -> onTouch(ev); true }

        addView(pill, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    // ── Gestures ─────────────────────────────────────────────────────────────

    private fun onTouch(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY; downT = ev.eventTime; isLong = false
                postDelayed(longCb, 500)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longCb)
                if (isLong) return
                val dx = ev.rawX - downX; val dy = ev.rawY - downY; val dt = ev.eventTime - downT
                when {
                    abs(dx) > dp(28) && abs(dx) > abs(dy) ->
                        if (dx < 0) c.onSwipeLeft() else c.onSwipeRight()
                    dy < -dp(20) -> c.onSwipeUp()
                    abs(dx) < dp(12) && abs(dy) < dp(12) && dt < 400 -> c.onTap()
                }
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(longCb)
        }
    }

    // ── Bind ─────────────────────────────────────────────────────────────────

    fun bind(e: IslandEvent, expanded: Boolean) {
        applyContent(e, expanded)
        val dm   = context.resources.displayMetrics
        val maxW = if (expanded) minOf(dm.widthPixels - dp(32), dp(440))
                   else (dm.widthPixels * 0.74f).toInt()
        pill.measure(
            MeasureSpec.makeMeasureSpec(maxW,
                if (expanded) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val isIdle = e.style == IslandStyle.IDLE
        val tw = if (isIdle) dp(Prefs.idleW(context)) else pill.measuredWidth
        val th = if (isIdle) dp(Prefs.idleH(context)) else pill.measuredHeight
        val p  = pill.layoutParams as FrameLayout.LayoutParams
        if (!shown) {
            p.width = tw; p.height = th; pill.layoutParams = p; setRadius(th); shown = true
        } else {
            val collapsing = curExpanded && !expanded
            if (collapsing) applyContent(e, true)
            animateTo(tw, th) { if (collapsing) applyContent(e, false) }
        }
        curExpanded = expanded
        if (c.typing && e.onReply != null) {
            if (!wasTyping) {
                wasTyping = true
                postDelayed({
                    replyEt.requestFocus()
                    context.getSystemService(InputMethodManager::class.java).showSoftInput(replyEt, 0)
                }, 150)
            }
        } else {
            wasTyping = false
        }
    }

    private fun idleMode() {
        hRow.visibility = View.GONE
        actRow.visibility = View.GONE
        menuBox.visibility = View.GONE
        replyBox.visibility = View.GONE
        pill.setPadding(0, 0, 0, 0)
        pill.minimumWidth = 0
    }

    private fun normalMode() {
        hRow.visibility = View.VISIBLE
        pill.setPadding(dp(12), dp(9), dp(12), dp(9))
        pill.minimumWidth = dp(90)
        menuBox.removeAllViews()
        menuBox.visibility = View.GONE
        replyBox.visibility = View.GONE
    }

    fun hideKeyboard() {
        try {
            context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
        } catch (ex: Exception) {
        }
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (ev.keyCode == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP && c.typing) {
            c.endTyping()
            return true
        }
        return super.dispatchKeyEvent(ev)
    }

    private fun applyContent(e: IslandEvent, exp: Boolean) {
        if (e.style == IslandStyle.IDLE) { idleMode(); return }
        normalMode()
        bodyTv.maxLines = if (exp) 6 else 2
        // reset every sub-view
        icon.visibility     = View.GONE
        bodyTv.visibility   = View.GONE
        subTv.visibility    = View.GONE
        chron.visibility    = View.GONE; chron.stop(); chron.isCountDown = false
        pBarH.visibility    = View.GONE
        navRow.visibility   = View.GONE
        eqView.visibility   = View.GONE
        waveView.visibility = View.GONE
        rProg.visibility    = View.GONE
        scanRing.visibility = View.GONE
        accTv.visibility    = View.GONE
        dot.visibility      = View.GONE
        actRow.removeAllViews(); actRow.visibility = View.GONE

        // icon
        if (e.icon != null) {
            icon.setImageDrawable(e.icon); icon.clearColorFilter(); icon.visibility = View.VISIBLE
        } else if (e.iconRes != 0) {
            icon.setImageResource(e.iconRes)
            if (e.iconTint != 0) icon.setColorFilter(e.iconTint) else icon.clearColorFilter()
            icon.visibility = View.VISIBLE
        }

        titleTv.text = e.title

        when (e.style) {
            IslandStyle.CALL -> {
                showBody(e); showChron(e)
                waveView.visibility = View.VISIBLE
                if (exp) showActions(e)
            }
            IslandStyle.MEDIA -> {
                showBody(e)
                eqView.visibility = View.VISIBLE
                if (exp) showActions(e)
            }
            IslandStyle.NAV -> {
                if (e.navTurnIcon != 0) navIcon.setImageResource(e.navTurnIcon)
                navDist.text = e.accessoryText ?: ""; navRow.visibility = View.VISIBLE
                showBody(e)
            }
            IslandStyle.TIMER -> {
                showChron(e); showBody(e)
                if (exp) showActions(e)
            }
            IslandStyle.PROGRESS -> {
                val pct = if (e.progressMax > 0) e.progressVal * 100 / e.progressMax else 0
                if (exp) {
                    pBarH.progress = pct; pBarH.visibility = View.VISIBLE
                } else {
                    rProg.pct = pct / 100f; rProg.visibility = View.VISIBLE
                }
                accTv.text = "$pct%"
                accTv.setTextColor(Color.rgb(34, 211, 238)); accTv.visibility = View.VISIBLE
                showBody(e)
            }
            IslandStyle.FACE_SCAN, IslandStyle.PAYMENT -> {
                scanRing.visibility = View.VISIBLE
                if (e.style == IslandStyle.PAYMENT) {
                    accTv.text = "✓"; accTv.setTextColor(Color.rgb(52,199,89))
                    accTv.textSize = 16f; accTv.visibility = View.VISIBLE
                }
                showBody(e)
            }
            IslandStyle.BT_BATTERY -> {
                if (e.batteryPct >= 0) {
                    accTv.text = "${e.batteryPct}%"
                    accTv.setTextColor(btColor(e.batteryPct)); accTv.visibility = View.VISIBLE
                }
                showBody(e)
            }
            IslandStyle.PRIVACY -> {
                dot.visibility = View.VISIBLE
                showBody(e)
            }
            IslandStyle.SPLIT -> {
                dot.visibility = View.VISIBLE; showBody(e); showChron(e)
                if (exp) showActions(e)
            }
            IslandStyle.MENU -> {
                buildMenu(e)
            }
            IslandStyle.HOTSPOT, IslandStyle.LIVE_TILE -> {
                showBody(e)
                if (!e.subText.isNullOrEmpty()) {
                    subTv.text = e.subText; subTv.visibility = View.VISIBLE
                }
                if (!e.accessoryText.isNullOrEmpty()) {
                    accTv.text = e.accessoryText
                    if (e.accessoryColor != -1) accTv.setTextColor(e.accessoryColor)
                    accTv.visibility = View.VISIBLE
                }
                if (exp) showActions(e)
            }
            else -> { // DEFAULT
                showReply(e, exp)
                showChron(e)
                if (!e.accessoryText.isNullOrEmpty()) {
                    accTv.text = e.accessoryText
                    if (e.accessoryColor != -1) accTv.setTextColor(e.accessoryColor)
                    accTv.visibility = View.VISIBLE
                }
                showBody(e)
                if (exp) showActions(e)
            }
        }
    }

    private fun showReply(e: IslandEvent, exp: Boolean) {
        if (!exp || e.onReply == null) return
        if (c.typing) {
            replyBox.visibility = View.VISIBLE
        } else {
            actRow.addView(buildAction(IslandAction("\u21A9 Reply") { c.beginTyping() }))
            actRow.visibility = View.VISIBLE
        }
    }

    private fun buildMenu(e: IslandEvent) {
        menuBox.removeAllViews()
        for (row in e.actions.chunked(4)) {
            val r = LinearLayout(context)
            r.orientation = LinearLayout.HORIZONTAL
            for (a in row) {
                val t = TextView(context)
                t.text = a.label ?: ""; t.setTextColor(Color.WHITE); t.textSize = 12f; t.gravity = Gravity.CENTER
                val g = GradientDrawable(); g.cornerRadius = dp(16).toFloat(); g.setColor(0xFF1F1F24.toInt())
                t.background = g
                t.setOnClickListener { a.onClick() }
                r.addView(t, LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            repeat(4 - row.size) {
                r.addView(View(context), LinearLayout.LayoutParams(0, dp(56), 1f).also { it.setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            menuBox.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        menuBox.visibility = View.VISIBLE
    }

    private fun showBody(e: IslandEvent) {
        if (!e.text.isNullOrEmpty()) { bodyTv.text = e.text; bodyTv.visibility = View.VISIBLE }
    }
    private fun showChron(e: IslandEvent) {
        if (e.chronoBase == 0L) return
        chron.base = e.chronoBase
        if (e.chronoCountDown) chron.isCountDown = true
        chron.start(); chron.visibility = View.VISIBLE
    }
    private fun showActions(e: IslandEvent) {
        if (e.actions.isEmpty() || c.typing) return
        e.actions.forEach { actRow.addView(buildAction(it)) }
        actRow.visibility = View.VISIBLE
    }

    private fun btColor(pct: Int) = when {
        pct <= 20 -> Color.rgb(255, 69, 58)
        pct <= 40 -> Color.rgb(255, 159, 10)
        else      -> Color.rgb(52, 199, 89)
    }

    private fun buildAction(a: IslandAction): View {
        return if (a.iconRes != 0) {
            ImageView(context).apply {
                setImageResource(a.iconRes); setColorFilter(Color.WHITE)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                val g = GradientDrawable(); g.shape = GradientDrawable.OVAL
                g.setColor(0xFF2C2C2E.toInt()); background = g
                setOnClickListener { a.onClick() }
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).also {
                    it.marginStart = dp(6); it.marginEnd = dp(6) }
            }
        } else {
            TextView(context).apply {
                text = a.label ?: ""; setTextColor(Color.WHITE); textSize = 13.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(12), 0, dp(12), 0)
                val g = GradientDrawable(); g.cornerRadius = dp(18).toFloat()
                g.setColor(if (a.color != 0) a.color else 0xFF2C2C2E.toInt()); background = g
                setOnClickListener { a.onClick() }
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).also {
                    it.marginStart = dp(4); it.marginEnd = dp(4) }
            }
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private fun setRadius(h: Int) {
        pillBg.cornerRadius = minOf(h / 2f, dp(30).toFloat()); pill.invalidateOutline()
    }

    private fun animateTo(w: Int, h: Int, onDone: () -> Unit) {
        sizeAnim?.cancel(); sizeAnim = null
        val p  = pill.layoutParams as FrameLayout.LayoutParams
        val w0 = p.width; val h0 = p.height
        if (w0 == w && h0 == h) { onDone(); return }
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = 340; va.interpolator = OvershootInterpolator(0.8f)
        va.addUpdateListener {
            val f = it.animatedValue as Float
            p.width  = (w0 + (w - w0) * f).toInt()
            p.height = (h0 + (h - h0) * f).toInt()
            pill.layoutParams = p; setRadius(p.height)
        }
        va.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                if (sizeAnim !== va) return; sizeAnim = null
                p.width = w; p.height = h; pill.layoutParams = p; setRadius(h); onDone()
            }
        })
        sizeAnim = va; va.start()
    }

    fun animateIn() {
        alpha = 0f; translationY = -dp(14).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
    }
    fun animateOut(end: () -> Unit) {
        sizeAnim?.cancel(); sizeAnim = null
        animate().alpha(0f).translationY(-dp(8).toFloat()).setDuration(160)
            .withEndAction { end() }.start()
    }
}
