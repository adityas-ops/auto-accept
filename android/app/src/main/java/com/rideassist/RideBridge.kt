package com.rideassist

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.ViewManager

object RideBridge {
    @Volatile var ctx: ReactApplicationContext? = null
    fun emit(name: String, payload: String) {
        try {
            ctx?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)?.emit(name, payload)
        } catch (_: Throwable) {}
    }
}

class RideAssistModule(private val rc: ReactApplicationContext) : ReactContextBaseJavaModule(rc) {
    init { RideBridge.ctx = rc }
    override fun getName() = "RideAssist"

    @ReactMethod fun addListener(n: String) {}
    @ReactMethod fun removeListeners(c: Int) {}

    @ReactMethod fun getConfig(p: Promise) = p.resolve(RideConfig.raw(rc))
    @ReactMethod fun setConfig(json: String, p: Promise) {
        try { RideConfig.save(rc, json); p.resolve(null) } catch (e: Exception) { p.reject("bad_config", e) }
    }
    @ReactMethod fun getHistory(p: Promise) = p.resolve(RideHistory.get(rc))
    @ReactMethod fun clearHistory(p: Promise) { RideHistory.clear(rc); p.resolve(null) }

    @ReactMethod fun status(p: Promise) {
        val m = Arguments.createMap()
        m.putBoolean("accessibility", RideAccessibilityService.instance != null)
        m.putBoolean("overlay", Settings.canDrawOverlays(rc))
        val pm = rc.getSystemService(android.os.PowerManager::class.java)
        m.putBoolean("battery", pm.isIgnoringBatteryOptimizations(rc.packageName))
        p.resolve(m)
    }

    private fun launch(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); rc.startActivity(i) }
    @ReactMethod fun openAccessibilitySettings() = launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    @ReactMethod fun openOverlaySettings() =
        launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${rc.packageName}")))
    @ReactMethod fun openBatterySettings() =
        launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${rc.packageName}")))

    @ReactMethod fun getInstalledApps(p: Promise) {
        val pm = rc.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val arr = Arguments.createArray()
        pm.queryIntentActivities(q, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != rc.packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
            .forEach { (pkg, label) -> arr.pushMap(Arguments.createMap().apply { putString("pkg", pkg); putString("label", label) }) }
        p.resolve(arr)
    }

    @ReactMethod fun startService() {
        val i = Intent(rc, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) rc.startForegroundService(i) else rc.startService(i)
    }
    @ReactMethod fun stopService() { rc.stopService(Intent(rc, OverlayService::class.java)) }
}

class RideAssistPackage : ReactPackage {
    override fun createNativeModules(c: ReactApplicationContext): List<NativeModule> = listOf(RideAssistModule(c))
    override fun createViewManagers(c: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}
