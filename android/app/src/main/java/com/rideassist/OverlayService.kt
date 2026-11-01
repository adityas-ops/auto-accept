package com.rideassist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** Foreground service keeping the engine alive plus a draggable on/off bubble. */
class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var bubble: TextView? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (RideConfig.load(this).overlay && android.provider.Settings.canDrawOverlays(this)) showBubble()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CH, "RideAssist", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CH).setContentTitle("RideAssist running")
            .setSmallIcon(android.R.drawable.ic_menu_directions).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun refresh() {
        val on = RideConfig.load(this).enabled
        bubble?.text = if (on) "ON" else "OFF"
        (bubble?.background as? GradientDrawable)?.setColor(if (on) Color.parseColor("#2E7D32") else Color.parseColor("#B71C1C"))
    }

    private fun showBubble() {
        if (bubble != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            140, 140, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 300 }
        val v = TextView(this).apply {
            gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 14f
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        }
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - tx) > 10 || Math.abs(e.rawY - ty) > 10) moved = true
                    lp.x = sx + (e.rawX - tx).toInt(); lp.y = sy + (e.rawY - ty).toInt()
                    wm?.updateViewLayout(v, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    val raw = org.json.JSONObject(RideConfig.raw(this))
                    raw.put("enabled", !raw.optBoolean("enabled"))
                    RideConfig.save(this, raw.toString()); refresh()
                    RideBridge.emit("config", raw.toString())
                }
            }
            true
        }
        bubble = v; wm?.addView(v, lp); refresh()
    }

    override fun onDestroy() {
        bubble?.let { wm?.removeView(it) }; bubble = null
        super.onDestroy()
    }

    companion object { const val CH = "rideassist" }
}
