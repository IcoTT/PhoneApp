package com.example.phoneapp

import android.content.Context

fun isMonitoringEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("monitoring_enabled", false)
}

fun setMonitoringEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("monitoring_enabled", enabled).apply()
}