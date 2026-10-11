package com.digiforgedynamics.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** The black pill. Compact = one line, expanded = title + text + action buttons. */
class IslandView(ctx: Context, private val c: IslandController) : FrameLayout(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * d + 0.5f).toInt()

    private val pill = LinearLayout(ctx)
    private val bg = GradientDrawable()
    private val icon = ImageView(ctx)
    private val title = TextView(ctx)
    private val body = TextView(ctx)
    private val accessory = FrameLayout(ctx)
    private val actionRow = LinearLayout(ctx)

    private var anim: ValueAnimator? = null
    private var shown = false
    private var curExpanded = false
    private var downY = 0f
    private var downT = 0L

    init {
        bg.setColor(Color.BLACK)
        bg.cornerRadius = dp(18).toFloat()
        pill.background = bg
        pill.orientation = LinearLayout.VERTICAL
        pill.setPadding(dp(12), dp(8), dp(12), dp(8))
        pill.clipToOutline = true
        pill.minimumWidth = dp(96)

        val header = LinearLayout(ctx)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        icon.clipToOutline = true
        icon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: Outline?) {
                if (view == null || outline == null) return
                outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.28f)
            }
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)))

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        title.setTextColor(Color.WHITE)
        title.textSize = 14f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        body.setTextColor(0xFFB5B5BA.toInt())
        body.textSize = 13f
        body.maxLines = 3
        body.ellipsize = TextUtils.TruncateAt.END
        col.addView(title)
        col.addView(body)
        val cp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        cp.marginStart = dp(10)
        cp.marginEnd = dp(10)
        header.addView(col, cp)
        header.addView(
            accessory,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        actionRow.orientation = LinearLayout.HORIZONTAL
        actionRow.gravity = Gravity.CENTER

        pill.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val ap = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        ap.topMargin = dp(8)
        pill.addView(actionRow, ap)

        pill.setOnTouchListener { _, ev -> handleTouch(ev) }
        addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )
    }

    private fun handleTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.rawY
                downT = ev.eventTime
            }
            MotionEvent.ACTION_UP -> {
                val dy = ev.rawY - downY
                if (dy < -dp(20)) {
                    c.onSwipeUp()
                } else if (abs(dy) < dp(14)) {
                    if (ev.eventTime - downT > 500) c.onOpen() else c.onTap()
                }
            }
        }
        return true
    }

    // ---- content -----------------------------------------------------------------------------

    fun bind(e: IslandEvent, expanded: Boolean) {
        title.text = e.title
        body.text = e.text ?: ""

        val ic = e.icon
        if (ic != null) {
            icon.setImageDrawable(ic)
            icon.clearColorFilter()
            icon.visibility = View.VISIBLE
        } else if (e.iconRes != 0) {
            icon.setImageResource(e.iconRes)
            if (e.iconTint != 0) icon.setColorFilter(e.iconTint) else icon.clearColorFilter()
            icon.visibility = View.VISIBLE
        } else {
            icon.visibility = View.GONE
        }

        accessory.removeAllViews()
        if (e.chronoBase != 0L) {
            val ch = Chronometer(context)
            ch.setTextColor(Color.rgb(52, 199, 89))
            ch.textSize = 13f
            ch.base = e.chronoBase
            ch.start()
            accessory.addView(ch)
        } else if (e.equalizer) {
            accessory.addView(EqualizerView(context), FrameLayout.LayoutParams(dp(22), dp(16)))
        } else if (!e.accessoryText.isNullOrEmpty()) {
            val t = TextView(context)
            t.text = e.accessoryText
            t.textSize = 13f
            t.setTypeface(t.typeface, Typeface.BOLD)
            t.setTextColor(e.accessoryColor)
            accessory.addView(t)
        }
        accessory.visibility = if (accessory.childCount > 0) View.VISIBLE else View.GONE

        actionRow.removeAllViews()
        for (a in e.actions) actionRow.addView(buildAction(a))

        // Measure the target size
        val dm = context.resources.displayMetrics
        applyVisibility(e, expanded)
        val maxW = if (expanded) min(dm.widthPixels - dp(32), dp(420)) else (dm.widthPixels * 0.72f).toInt()
        pill.measure(
            View.MeasureSpec.makeMeasureSpec(
                maxW,
                if (expanded) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST
            ),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val tw = pill.measuredWidth
        val th = pill.measuredHeight

        val p = pill.layoutParams as FrameLayout.LayoutParams
        if (!shown) {
            p.width = tw
            p.height = th
            pill.layoutParams = p
            setRadius(th)
            shown = true
        } else {
            val collapsing = curExpanded && !expanded
            if (collapsing) applyVisibility(e, true) // keep content visible while shrinking
            animateTo(tw, th) {
                if (collapsing) applyVisibility(e, false)
            }
        }
        curExpanded = expanded
    }

    private fun applyVisibility(e: IslandEvent, expanded: Boolean) {
        body.visibility = if (expanded && !e.text.isNullOrEmpty()) View.VISIBLE else View.GONE
        actionRow.visibility = if (expanded && e.actions.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildAction(a: IslandAction): View {
        if (a.iconRes != 0) {
            val b = ImageView(context)
            b.setImageResource(a.iconRes)
            b.setColorFilter(Color.WHITE)
            b.setPadding(dp(11), dp(11), dp(11), dp(11))
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(0xFF2C2C2E.toInt())
            b.background = g
            b.setOnClickListener { a.onClick() }
            val lp = LinearLayout.LayoutParams(dp(46), dp(46))
            lp.marginStart = dp(8)
            lp.marginEnd = dp(8)
            b.layoutParams = lp
            return b
        }
        val t = TextView(context)
        t.text = a.label ?: ""
        t.setTextColor(Color.WHITE)
        t.textSize = 14f
        t.gravity = Gravity.CENTER
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(dp(14), 0, dp(14), 0)
        val g = GradientDrawable()
        g.cornerRadius = dp(20).toFloat()
        g.setColor(if (a.color != 0) a.color else 0xFF2C2C2E.toInt())
        t.background = g
        t.setOnClickListener { a.onClick() }
        val lp = LinearLayout.LayoutParams(0, dp(40), 1f)
        lp.marginStart = dp(4)
        lp.marginEnd = dp(4)
        t.layoutParams = lp
        return t
    }

    // ---- animation ---------------------------------------------------------------------------

    private fun setRadius(h: Int) {
        bg.cornerRadius = min(h / 2f, dp(30).toFloat())
        pill.invalidateOutline()
    }

    private fun animateTo(w: Int, h: Int, onDone: () -> Unit) {
        val old = anim
        anim = null
        old?.cancel()
        val p = pill.layoutParams as FrameLayout.LayoutParams
        val w0 = p.width
        val h0 = p.height
        if (w0 == w && h0 == h) {
            onDone()
            return
        }
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = 340
        va.interpolator = OvershootInterpolator(0.9f)
        va.addUpdateListener {
            val f = it.animatedValue as Float
            p.width = (w0 + (w - w0) * f).toInt()
            p.height = (h0 + (h - h0) * f).toInt()
            pill.layoutParams = p
            setRadius(p.height)
        }
        va.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (anim !== va) return
                anim = null
                p.width = w
                p.height = h
                pill.layoutParams = p
                setRadius(h)
                onDone()
            }
        })
        anim = va
        va.start()
    }

    fun animateIn() {
        alpha = 0f
        translationY = -dp(16).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun animateOut(end: () -> Unit) {
        anim?.cancel()
        anim = null
        animate().alpha(0f).translationY(-dp(10).toFloat()).setDuration(160)
            .withEndAction { end() }.start()
    }
}

/** Tiny 3-bar "music is playing" indicator. Ticks at ~11 fps and only while attached. */
class EqualizerView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val tick = object : Runnable {
        override fun run() {
            t += 0.35f
            invalidate()
            postDelayed(this, 90)
        }
    }

    init {
        paint.color = Color.rgb(34, 211, 238)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(tick, 90)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = w / 5f
        for (i in 0..2) {
            val amp = 0.3f + 0.7f * abs(sin(t + i * 1.7f))
            val bh = h * amp
            canvas.drawRoundRect(i * 2 * bw, h - bh, i * 2 * bw + bw, h, bw / 2, bw / 2, paint)
        }
    }
}
