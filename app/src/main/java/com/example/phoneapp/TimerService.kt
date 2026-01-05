package com.example.phoneapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat

class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var timeElapsed = 0
    private var timeLimit = 10 // minutes
    private var firstNotificationSent = false
    private var monitoredApps = setOf<String>()
    private var wasMonitoredAppActive = false

    companion object {
        const val CHANNEL_ID = "social_detox_channel"
        const val NOTIFICATION_ID = 1
        const val SUBSEQUENT_INTERVAL = 180 // 3 minutes in seconds
        const val CHECK_INTERVAL = 1000L // Check every 1 second
    }

    private val techniques = listOf(
        "Take a deep breath. Inhale for 4 seconds, hold for 4, exhale for 4.",
        "Listen deeply to the most silent sound you can hear around you.",
        "Feel your feet on the ground. Notice the sensation for 10 seconds.",
        "Look away from the screen. Find 3 things of the same color in your room.",
        "Roll your shoulders back slowly. Release the tension you're holding.",
        "Close your eyes for 10 seconds. Just be present with yourself.",
        "Ask yourself: What do I really need right now?",
        "Think of one person you're grateful for today.",
        "Notice your posture. Sit up straight and take a slow breath.",
        "Put your hand on your heart. Feel it beating for a moment."
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Load settings from preferences
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        timeLimit = prefs.getInt("time_limit", 10)
        monitoredApps = prefs.getStringSet("monitored_apps", emptySet()) ?: emptySet()

        // Reset state
        timeElapsed = 0
        firstNotificationSent = false
        wasMonitoredAppActive = false

        // Start foreground service with notification
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start monitoring
        startMonitoring()

        return START_STICKY
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                // Check if a monitored app is currently in foreground
                val currentApp = getForegroundApp()
                val isMonitoredAppActive = monitoredApps.contains(currentApp)

                if (isMonitoredAppActive) {
                    // Monitored app is active - count time
                    timeElapsed++
                    wasMonitoredAppActive = true

                    val timeLimitSeconds = timeLimit * 60

                    // First notification when time limit reached
                    if (timeElapsed >= timeLimitSeconds && !firstNotificationSent) {
                        showFirstMessage()
                        firstNotificationSent = true
                    }

                    // Subsequent notifications every 3 minutes after first
                    if (firstNotificationSent) {
                        val timeAfterFirst = timeElapsed - timeLimitSeconds
                        if (timeAfterFirst > 0 && timeAfterFirst % SUBSEQUENT_INTERVAL == 0) {
                            showTechniqueMessage()
                        }
                    }

                    // Update foreground notification with time
                    updateForegroundNotification(timeElapsed)

                } else if (wasMonitoredAppActive) {
                    // User left monitored app - RESET timer!
                    timeElapsed = 0
                    firstNotificationSent = false
                    wasMonitoredAppActive = false

                    // Update notification to show reset
                    updateForegroundNotificationIdle()
                }

                // Continue checking every second
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        })
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 // Last 1 minute

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            return null
        }

        // Find the most recently used app
        var recentApp: String? = null
        var recentTime = 0L

        for (usageStats in usageStatsList) {
            if (usageStats.lastTimeUsed > recentTime) {
                recentTime = usageStats.lastTimeUsed
                recentApp = usageStats.packageName
            }
        }

        return recentApp
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Social Detox Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when timer is running"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Social Detox Active")
            .setContentText("Waiting for monitored app...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification(seconds: Int) {
        val minutes = seconds / 60
        val secs = seconds % 60
        val timeText = String.format("%d:%02d", minutes, secs)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Social Detox Active")
            .setContentText("Uninterrupted time: $timeText")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateForegroundNotificationIdle() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Social Detox Active")
            .setContentText("Great! You took a break. Timer reset.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showFirstMessage() {
        val title = "Hey, just a gentle reminder 💙"
        val message = "You've been on for $timeLimit minutes. Maybe a good moment for a break?"
        showOverlayOrNotification(title, message)
    }

    private fun showTechniqueMessage() {
        val title = "Try this quick technique ✨"
        val message = techniques.random()
        showOverlayOrNotification(title, message)
    }

    private fun showOverlayOrNotification(title: String, message: String) {
        if (Settings.canDrawOverlays(this)) {
            // Show overlay
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_TITLE, title)
                putExtra(OverlayService.EXTRA_MESSAGE, message)
            }
            startService(intent)
        } else {
            // Fallback to notification
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}