package com.example.phoneapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only start if monitoring was enabled before reboot
            if (isMonitoringEnabled(context)) {
                val serviceIntent = Intent(context, TimerService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}