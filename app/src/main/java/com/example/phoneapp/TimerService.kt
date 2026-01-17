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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var timeElapsed = 0
    private var timeLimit = 10 // minutes
    private var timeLimitReachedThisSession = false
    private var monitoredApps = setOf<String>()

    // Break tracking
    private var breakStartTime: Long? = null
    private val MINIMUM_BREAK_SECONDS = 30

    companion object {
        const val CHANNEL_ID = "social_detox_channel"
        const val NOTIFICATION_ID = 1
        const val SUBSEQUENT_INTERVAL = 180 // 3 minutes in seconds
        const val CHECK_INTERVAL = 1000L // Check every 1 second
        const val PREF_FIRST_MESSAGE_DATE = "first_message_date"
    }

    // Techniques as Pair(subheading, body)
    private val techniques = listOf(
        Pair(
            "Support contact with yourself.",
            "This will weaken the algorithm's influence on your will.\n" +
                    "Let's do it like this:\n" +
                    "→ Take 5 deep breaths\n" +
                    "→ Watch your belly rise and fall\n" +
                    "You've just interrupted the flood of cheap dopamine. If you want, you can stop scrolling."
        )
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

        // Reset session state
        timeElapsed = 0
        timeLimitReachedThisSession = false
        breakStartTime = null

        // Start foreground service with notification
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start monitoring
        startMonitoring()

        return START_STICKY
    }

    private fun getTodayDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun hasShownFirstMessageToday(): Boolean {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lastDate = prefs.getString(PREF_FIRST_MESSAGE_DATE, null)
        return lastDate == getTodayDateString()
    }

    private fun markFirstMessageShownToday() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_FIRST_MESSAGE_DATE, getTodayDateString()).apply()
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                val currentApp = getForegroundApp()
                val isMonitoredAppActive = monitoredApps.contains(currentApp)

                if (isMonitoredAppActive) {
                    // User is in a monitored app

                    if (breakStartTime != null) {
                        // User was on a break - check how long
                        val breakDuration = (System.currentTimeMillis() - breakStartTime!!) / 1000

                        if (breakDuration >= MINIMUM_BREAK_SECONDS) {
                            // Real break (30+ seconds) - reset timer
                            timeElapsed = 0
                            timeLimitReachedThisSession = false
                        }
                        // Otherwise: short break - continue from where we left off

                        breakStartTime = null
                    }

                    // Count time
                    timeElapsed++

                    val timeLimitSeconds = timeLimit * 60

                    // Time limit reached for this session
                    if (timeElapsed >= timeLimitSeconds && !timeLimitReachedThisSession) {
                        timeLimitReachedThisSession = true
                        
                        // Show first message only if not shown today
                        if (!hasShownFirstMessageToday()) {
                            showFirstMessage()
                            markFirstMessageShownToday()
                        } else {
                            // Already shown first message today, show technique instead
                            showTechniqueMessage()
                        }
                    }

                    // Subsequent notifications every 3 minutes after time limit reached
                    if (timeLimitReachedThisSession) {
                        val timeAfterLimit = timeElapsed - timeLimitSeconds
                        if (timeAfterLimit > 0 && timeAfterLimit % SUBSEQUENT_INTERVAL == 0) {
                            showTechniqueMessage()
                        }
                    }

                    // Update notification
                    updateForegroundNotification(timeElapsed)

                } else {
                    // User is NOT in a monitored app

                    if (breakStartTime == null && timeElapsed > 0) {
                        // Just started a break - record the time
                        breakStartTime = System.currentTimeMillis()
                        updateForegroundNotificationOnBreak()
                    } else if (breakStartTime != null) {
                        // Already on break - check if it's a real break now
                        val breakDuration = (System.currentTimeMillis() - breakStartTime!!) / 1000

                        if (breakDuration >= MINIMUM_BREAK_SECONDS) {
                            // Real break achieved!
                            timeElapsed = 0
                            timeLimitReachedThisSession = false
                            breakStartTime = null
                            updateForegroundNotificationBreakComplete()
                        } else {
                            // Still in short break window
                            updateForegroundNotificationOnBreak()
                        }
                    }
                }

                // Continue checking every second
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        })
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            return null
        }

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

    private fun updateForegroundNotificationOnBreak() {
        val minutes = timeElapsed / 60
        val secs = timeElapsed % 60
        val timeText = String.format("%d:%02d", minutes, secs)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Social Detox - Paused")
            .setContentText("Timer paused at $timeText. Stay away for 30s to reset!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateForegroundNotificationBreakComplete() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Social Detox Active")
            .setContentText("Great! You took a real break. Timer reset. 🎉")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showFirstMessage() {
        val title = "Hey, just a gentle reminder 💙"
        val message = "You've been on for $timeLimit minutes. Maybe a good moment for a break?"
        showOverlayOrNotification(title, "", message)
    }

    private fun showTechniqueMessage() {
        val title = "Try this quick technique ✨"
        val technique = techniques.random()
        showOverlayOrNotification(title, technique.first, technique.second)
    }

    private fun showOverlayOrNotification(title: String, subheading: String, message: String) {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_TITLE, title)
                putExtra(OverlayService.EXTRA_SUBHEADING, subheading)
                putExtra(OverlayService.EXTRA_MESSAGE, message)
            }
            startService(intent)
        } else {
            // For notifications, combine subheading and message
            val fullMessage = if (subheading.isNotEmpty()) "$subheading\n$message" else message
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(fullMessage)
                .setStyle(NotificationCompat.BigTextStyle().bigText(fullMessage))
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
