package com.rideassist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RideHistory {
    private const val KEY = "history"
    private const val MAX = 200

    @Synchronized
    fun add(ctx: Context, entry: JSONObject) {
        val p = ctx.getSharedPreferences(RideConfig.PREFS, Context.MODE_PRIVATE)
        val old = JSONArray(p.getString(KEY, "[]"))
        val out = JSONArray().put(entry)
        for (i in 0 until minOf(old.length(), MAX - 1)) out.put(old.get(i))
        p.edit().putString(KEY, out.toString()).apply()
    }

    fun get(ctx: Context): String =
        ctx.getSharedPreferences(RideConfig.PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"

    fun clear(ctx: Context) =
        ctx.getSharedPreferences(RideConfig.PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
}
