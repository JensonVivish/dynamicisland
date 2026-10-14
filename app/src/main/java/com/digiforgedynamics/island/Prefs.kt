package com.digiforgedynamics.island

import android.content.Context

object Prefs {
    const val ENABLED        = "enabled"
    const val NOTIFS         = "notifs"
    const val MEDIA          = "media"
    const val CHARGING       = "charging"
    const val PRIVACY_DOTS   = "privacy_dots"
    const val BT_BATTERY     = "bt_battery"
    const val NAV            = "nav"
    const val LIVE_TILES     = "live_tiles"
    const val HIDE_LANDSCAPE = "hide_landscape"
    const val TOP            = "top_dp"
    const val X              = "x_dp"
    const val HOLD           = "hold_s"
    const val KEEP_ALIVE     = "keep_alive"

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)

    fun bool(c: Context, k: String, d: Boolean = true): Boolean = sp(c).getBoolean(k, d)
    fun setBool(c: Context, k: String, v: Boolean) { sp(c).edit().putBoolean(k, v).apply() }
    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun setInt(c: Context, k: String, v: Int) { sp(c).edit().putInt(k, v).apply() }

    fun keepAlive(c: Context)    = bool(c, KEEP_ALIVE, false)
    fun enabled(c: Context)      = bool(c, ENABLED)
    fun notifs(c: Context)       = bool(c, NOTIFS)
    fun media(c: Context)        = bool(c, MEDIA)
    fun charging(c: Context)     = bool(c, CHARGING)
    fun privacyDots(c: Context)  = bool(c, PRIVACY_DOTS)
    fun btBattery(c: Context)    = bool(c, BT_BATTERY)
    fun nav(c: Context)          = bool(c, NAV)
    fun liveTiles(c: Context)    = bool(c, LIVE_TILES)
    fun hideLandscape(c: Context)= bool(c, HIDE_LANDSCAPE)
    fun topDp(c: Context)        = int(c, TOP, 8)
    fun xDp(c: Context)          = int(c, X, 0)
    fun holdMs(c: Context): Long = int(c, HOLD, 5) * 1000L

    fun isBlocked(c: Context, pkg: String): Boolean =
        sp(c).getStringSet("blocked", null)?.contains(pkg) == true
    fun setBlocked(c: Context, pkg: String, blocked: Boolean) {
        val s = HashSet<String>(sp(c).getStringSet("blocked", null) ?: emptySet<String>())
        if (blocked) s.add(pkg) else s.remove(pkg)
        sp(c).edit().putStringSet("blocked", s).apply()
    }
}
