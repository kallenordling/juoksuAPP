package com.nordling.juoksu

import android.content.Context

data class Goal(val distanceMeters: Double, val targetSeconds: Int) {
    val targetSpeedMps: Double get() = distanceMeters / targetSeconds
    val paceSecPerKm: Double get() = targetSeconds / (distanceMeters / 1000.0)
}

data class GoalPreset(val name: String, val meters: Double)

object GoalPresets {
    val ALL = listOf(
        GoalPreset("5 km", 5000.0),
        GoalPreset("10 km", 10000.0),
        GoalPreset("Half marathon", 21097.5),
        GoalPreset("Marathon", 42195.0),
    )
}

object GoalStore {
    private const val PREFS = "juoksu_goal"
    private const val KEY_DIST = "distance_m"
    private const val KEY_SEC = "target_s"

    fun get(ctx: Context): Goal? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sp.contains(KEY_DIST)) return null
        val d = java.lang.Double.longBitsToDouble(sp.getLong(KEY_DIST, 0L))
        val s = sp.getInt(KEY_SEC, 0)
        if (d <= 0 || s <= 0) return null
        return Goal(d, s)
    }

    fun set(ctx: Context, g: Goal) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DIST, java.lang.Double.doubleToRawLongBits(g.distanceMeters))
            .putInt(KEY_SEC, g.targetSeconds)
            .apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

fun formatPace(secPerKm: Double): String {
    if (secPerKm <= 0 || secPerKm.isInfinite() || secPerKm.isNaN()) return "—:— /km"
    val total = secPerKm.toInt()
    return "%d:%02d /km".format(total / 60, total % 60)
}

fun formatHms(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
