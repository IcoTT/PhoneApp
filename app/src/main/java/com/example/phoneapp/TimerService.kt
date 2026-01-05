package com.example.phoneapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var timeElapsed = 0
    private var timeLimit = 10 // minutes
    private var firstNotificationSent = false

    companion object {
        const val CHANNEL_ID = "social_detox_channel"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
        const val SUBSEQUENT_INTERVAL = 180 // 3 minutes in seconds
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
        // Load time limit from preferences
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        timeLimit = prefs.getInt("time_limit", 10)

        // Reset state
        timeElapsed = 0
        firstNotificationSent = false

        // Start foreground service with notification
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start timer
        startTimer()

        return START_STICKY
    }

    private fun startTimer() {
        handler.post(object : Runnable {
            override fun run() {
                timeElapsed++

                val timeLimitSeconds = timeLimit * 60

                // First notification when time limit reached
                if (timeElapsed >= timeLimitSeconds && !firstNotificationSent) {
                    showFirstNotification()
                    firstNotificationSent = true
                }

                // Subsequent notifications every 3 minutes after first
                if (firstNotificationSent) {
                    val timeAfterFirst = timeElapsed - timeLimitSeconds
                    if (timeAfterFirst > 0 && timeAfterFirst % SUBSEQUENT_INTERVAL == 0) {
                        showTechniqueNotification()
                    }
                }

                // Continue timer every second
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Social Detox Timer",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows when timer is running"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Social Detox Active")
            .setContentText("Monitoring your social media time...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun showFirstNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hey, just a gentle reminder 💙")
            .setContentText("You've been on for $timeLimit minutes. Maybe a good moment for a break?")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun showTechniqueNotification() {
        val technique = techniques.random()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Try this quick technique ✨")
            .setContentText(technique)
            .setStyle(NotificationCompat.BigTextStyle().bigText(technique))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}