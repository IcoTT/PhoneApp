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

    companion object {
        const val CHANNEL_ID = "social_detox_channel"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Load time limit from preferences
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        timeLimit = prefs.getInt("time_limit", 10)

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

                // Check if time limit reached (convert minutes to seconds)
                if (timeElapsed >= timeLimit * 60) {
                    showAlertNotification()
                    stopSelf()
                    return
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
            .setContentText("Monitoring your social media time...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun showAlertNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Time's Up!")
            .setContentText("You've reached your $timeLimit minute limit. Take a break!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
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