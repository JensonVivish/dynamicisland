package com.digiforgedynamics.island

import android.content.Context

object Prefs {
    const val ENABLED = "enabled"
    const val NOTIFS = "notifs"
    const val MEDIA = "media"
    const val CHARGING = "charging"
    const val HIDE_LANDSCAPE = "hide_landscape"
    const val TOP = "top_dp"
    const val X = "x_dp"
    const val HOLD = "hold_s"

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)

    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)
    fun setBool(c: Context, k: String, v: Boolean) {
        sp(c).edit().putBoolean(k, v).apply()
    }

    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun setInt(c: Context, k: String, v: Int) {
        sp(c).edit().putInt(k, v).apply()
    }

    fun enabled(c: Context) = bool(c, ENABLED, true)
    fun notifs(c: Context) = bool(c, NOTIFS, true)
    fun media(c: Context) = bool(c, MEDIA, true)
    fun charging(c: Context) = bool(c, CHARGING, true)
    fun hideLandscape(c: Context) = bool(c, HIDE_LANDSCAPE, true)
    fun topDp(c: Context) = int(c, TOP, 8)
    fun xDp(c: Context) = int(c, X, 0)
    fun holdMs(c: Context): Long = int(c, HOLD, 5) * 1000L

    fun isBlocked(c: Context, pkg: String): Boolean =
        sp(c).getStringSet("blocked", null)?.contains(pkg) == true

    fun setBlocked(c: Context, pkg: String, blocked: Boolean) {
        val s = HashSet<String>(sp(c).getStringSet("blocked", null) ?: emptySet<String>())
        if (blocked) s.add(pkg) else s.remove(pkg)
        sp(c).edit().putStringSet("blocked", s).apply()
    }
}
