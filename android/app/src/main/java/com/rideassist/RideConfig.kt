package com.rideassist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Settings written by the JS side as one JSON blob and read by the native engine. */
data class RideConfig(
    val enabled: Boolean,
    val packages: Set<String>,
    val acceptTexts: List<String>,
    val sliderTexts: List<String>,
    val minFare: Double,
    val minPerKm: Double,
    val maxPickupKm: Double,
    val minTripKm: Double,
    val maxTripKm: Double,
    val bookingTypes: Map<String, Boolean>,
    val blockedKeywords: List<String>,
    val allowedKeywords: List<String>,
    val testMode: Boolean,
    val delayMs: Long,
    val overlay: Boolean,
    val voice: Boolean,
    val vibrate: Boolean,
    val acceptUnknownFare: Boolean,
    val acceptBoost: Boolean,
) {
    companion object {
        const val PREFS = "rideassist"
        const val KEY = "config"

        val DEFAULT_JSON = """
        {"enabled":false,
         "packages":["com.rapido.rider","com.olacabs.oladriver"],
         "acceptTexts":["Accept","Accept Ride","Accept Order","Accept Request","Confirm","Take","स्वीकार करें","स्वीकार"],
         "sliderTexts":["Swipe to accept","Slide to accept","Swipe to confirm","Slide to confirm","Swipe right","Slide to start","स्वाइप करें"],
         "minFare":0,"minPerKm":0,"maxPickupKm":0,"minTripKm":0,"maxTripKm":0,
         "bookingTypes":{"bike":true,"auto":true,"cab":true,"parcel":true,"boost":true},
         "blockedKeywords":[],"allowedKeywords":[],"testMode":false,"delayMs":150,"overlay":true,"voice":false,"vibrate":true,
         "acceptUnknownFare":true,"acceptBoost":true}
        """.trimIndent()

        fun load(ctx: Context): RideConfig =
            parse(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: DEFAULT_JSON)

        fun save(ctx: Context, json: String) {
            JSONObject(json) // validate
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
        }

        fun raw(ctx: Context): String =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: DEFAULT_JSON

        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else List(a.length()) { a.getString(it) }

        fun parse(json: String): RideConfig {
            val o = JSONObject(json)
            val bt = o.optJSONObject("bookingTypes") ?: JSONObject()
            return RideConfig(
                enabled = o.optBoolean("enabled"),
                packages = strings(o.optJSONArray("packages")).toSet(),
                acceptTexts = strings(o.optJSONArray("acceptTexts")),
                sliderTexts = o.optJSONArray("sliderTexts")?.let { strings(it) }
                    ?: strings(JSONObject(DEFAULT_JSON).getJSONArray("sliderTexts")),
                minFare = o.optDouble("minFare", 0.0),
                minPerKm = o.optDouble("minPerKm", 0.0),
                maxPickupKm = o.optDouble("maxPickupKm", 0.0),
                minTripKm = o.optDouble("minTripKm", 0.0),
                maxTripKm = o.optDouble("maxTripKm", 0.0),
                bookingTypes = bt.keys().asSequence().associateWith { bt.optBoolean(it, true) },
                blockedKeywords = strings(o.optJSONArray("blockedKeywords")),
                allowedKeywords = strings(o.optJSONArray("allowedKeywords")),
                testMode = o.optBoolean("testMode"),
                delayMs = o.optLong("delayMs", 150),
                overlay = o.optBoolean("overlay", true),
                voice = o.optBoolean("voice"),
                vibrate = o.optBoolean("vibrate", true),
                acceptUnknownFare = o.optBoolean("acceptUnknownFare", true),
                acceptBoost = o.optBoolean("acceptBoost", true),
            )
        }
    }
}
