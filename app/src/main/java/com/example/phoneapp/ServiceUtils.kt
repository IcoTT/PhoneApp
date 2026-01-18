package com.example.phoneapp

import android.app.ActivityManager
import android.content.Context

fun isMonitoringEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("monitoring_enabled", false)
}

fun setMonitoringEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("monitoring_enabled", enabled).apply()
}

fun isServiceRunning(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
        if (TimerService::class.java.name == service.service.className) {
            return true
        }
    }
    return false
}
