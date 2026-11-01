package com.rideassist

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale

object RideFeedback {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun accepted(ctx: Context, cfg: RideConfig, card: RideCard) {
        if (cfg.vibrate) {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(200)
        }
        if (cfg.voice) speak(ctx.applicationContext, "Ride accepted. Fare ${card.fare?.toInt() ?: "unknown"} rupees")
    }

    private fun speak(ctx: Context, msg: String) {
        if (tts == null) {
            tts = TextToSpeech(ctx) { s ->
                ready = s == TextToSpeech.SUCCESS
                if (ready) { tts?.language = Locale.ENGLISH; tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "ra") }
            }
        } else if (ready) tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "ra")
    }
}
