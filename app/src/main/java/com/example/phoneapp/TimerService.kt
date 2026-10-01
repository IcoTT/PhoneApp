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

    // Technique rotation - ensures all techniques shown before repeating
    private var remainingTechniques = mutableListOf<Int>()

    companion object {
        const val CHANNEL_ID = "social_detox_channel"
        const val NOTIFICATION_ID = 1
        const val SUBSEQUENT_INTERVAL = 180 // 3 minutes in seconds
        const val CHECK_INTERVAL = 1000L // Check every 1 second
        const val PREF_FIRST_MESSAGE_DATE = "first_message_date"

        // Stats keys
        const val PREF_STATS_DATE = "stats_date"
        const val PREF_STATS_TIME_SECONDS = "stats_time_seconds"
        const val PREF_STATS_BREAKS = "stats_breaks"
        const val PREF_STATS_TECHNIQUES = "stats_techniques"
    }

    // Data class with Category attribute
    data class Technique(
        val category: String,
        val subheading: String,
        val body: String
    )

    private val techniques = listOf(

        // ── CATEGORY: Body brings you back (somatic) ──────────────────────────

        // 1
        Technique(
            category = "Body brings you back",
            subheading = "Breath",
            body = """
                Take 5 deep breaths.
                Watch your belly rise and fall.

                Done. You just interrupted the autopilot.
                You can leave.
            """.trimIndent()
        ),
        // 2
        Technique(
            category = "Body brings you back",
            subheading = "The quietest sound",
            body = """
                What's the quietest sound you can hear right now?
                Listen to it for 10 seconds.

                Done. The stream of thoughts has broken.
                You can leave.
            """.trimIndent()
        ),
        // 3
        Technique(
            category = "Body brings you back",
            subheading = "Your feet",
            body = """
                What do you feel under your feet?
                Floor? Carpet? Slippers?
                Notice it for 5 seconds.

                Done. You just stopped scrolling.
                You can leave.
            """.trimIndent()
        ),
        // 4
        Technique(
            category = "Body brings you back",
            subheading = "Phone in your hand",
            body = """
                What does your hand feel?
                The temperature of the phone. The smoothness of the glass. Its weight.
                Notice it for 5 seconds.

                Done. You've created space.
                You can leave.
            """.trimIndent()
        ),
        // 5
        Technique(
            category = "Body brings you back",
            subheading = "Your spine",
            body = """
                Find the place where your spine meets your head.
                Slowly move your attention all the way down your spine.

                Done. The algorithm just lost its power over you.
                You can leave.
            """.trimIndent()
        ),
        // 6
        Technique(
            category = "Body brings you back",
            subheading = "Edge of vision — CIA technique",
            body = """
                Without moving your head or eyes:
                Notice what you can see at the very edge of your field of vision.
                Hold it for 10 seconds.

                Done. Your stress just dropped.
                You can leave.
            """.trimIndent()
        ),
        // 7
        Technique(
            category = "Body brings you back",
            subheading = "Breath hold — challenge",
            body = """
                → Breathe in calmly.
                → Breathe out and hold.
                → Count how long you can last — no panic.

                Done. You've just opened a window of freedom.
                You can leave.
            """.trimIndent()
        ),

        // ── CATEGORY: Your future self is calling (time perspective) ──────────

        // 8
        Technique(
            category = "Your future self is calling",
            subheading = "In 30 minutes…",
            body = """
                In 30 minutes:
                → If you close Instagram now — how will you feel?
                → If you keep scrolling — how will you feel?

                Choose which YOU you want to be.
            """.trimIndent()
        ),
        // 9
        Technique(
            category = "Your future self is calling",
            subheading = "This moment",
            body = """
                Fun fact:
                → Instagram will still be here in an hour.
                → Tomorrow too.
                → Even in 10 years.

                But this moment of your life?
                You only get it once.

                What will you do with it?
            """.trimIndent()
        ),

        // ── CATEGORY: You see through manipulation (awareness) ────────────────

        // 10
        Technique(
            category = "You see through manipulation",
            subheading = "The algorithm is robbing you",
            body = """
                Let's be honest:
                The algorithm manipulates you and steals your time.
                Its one job = show you as many ads as possible.

                Will you let it?
            """.trimIndent()
        ),
        // 11
        Technique(
            category = "You see through manipulation",
            subheading = "Nothing is free",
            body = """
                Let's be honest:
                Social media isn't free.
                Companies pay big money for your time.
                You are the product.

                Will you let them?
            """.trimIndent()
        ),
        // 12
        Technique(
            category = "You see through manipulation",
            subheading = "Searching for nothing",
            body = """
                Let's be honest:
                The chance you'll find something truly valuable right now? Small.
                That's why you scroll… and find nothing.

                You can just stop.
            """.trimIndent()
        ),
        // 13
        Technique(
            category = "You see through manipulation",
            subheading = "What do you hate about this?",
            body = """
                Think about it:
                What's the most disgusting thing about endless scrolling?
                What's the real reason you want to scroll less?

                You have the chance to act on it. Right now.
            """.trimIndent()
        ),

        // ── CATEGORY: Pattern interrupt ───────────────────────────────────────

        // 14
        Technique(
            category = "Pattern interrupt",
            subheading = "5 breaths — challenge",
            body = """
                Challenge!
                Don't move to the next reel until you've taken 5 full breaths.
                5… 4… 3… 2… 1…

                Done. You've created space.
                Now you can continue. But you don't have to.
            """.trimIndent()
        ),
        // 15
        Technique(
            category = "Pattern interrupt",
            subheading = "It's completely OK",
            body = """
                It's completely normal that you don't feel like stopping.
                That's how it was designed.

                But don't forget:
                If you want, you can.
            """.trimIndent()
        ),

        // ── CATEGORY: Inner strength (bonus) ─────────────────────────────────

        // 16
        Technique(
            category = "Inner strength",
            subheading = "Your power",
            body = """
                Remember:
                When did you feel the most powerful in your life?
                Where in your body do you feel that strength?

                Feel it. 3 seconds.

                Use that power. Put your phone down.
            """.trimIndent()
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

    // Stats helper methods
    private fun resetStatsIfNewDay() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val statsDate = prefs.getString(PREF_STATS_DATE, null)
        val today = getTodayDateString()

        if (statsDate != today) {
            prefs.edit()
                .putString(PREF_STATS_DATE, today)
                .putInt(PREF_STATS_TIME_SECONDS, 0)
                .putInt(PREF_STATS_BREAKS, 0)
                .putInt(PREF_STATS_TECHNIQUES, 0)
                .apply()
        }
    }

    private fun incrementTimeOnApps() {
        resetStatsIfNewDay()
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentTime = prefs.getInt(PREF_STATS_TIME_SECONDS, 0)
        prefs.edit().putInt(PREF_STATS_TIME_SECONDS, currentTime + 1).apply()
    }

    private fun incrementBreaks() {
        resetStatsIfNewDay()
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentBreaks = prefs.getInt(PREF_STATS_BREAKS, 0)
        prefs.edit().putInt(PREF_STATS_BREAKS, currentBreaks + 1).apply()
    }

    private fun incrementTechniques() {
        resetStatsIfNewDay()
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val currentTechniques = prefs.getInt(PREF_STATS_TECHNIQUES, 0)
        prefs.edit().putInt(PREF_STATS_TECHNIQUES, currentTechniques + 1).apply()
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
                    incrementTimeOnApps()

                    val timeLimitSeconds = timeLimit * 60
                    val firstMessageAlreadyShown = hasShownFirstMessageToday()

                    if (!firstMessageAlreadyShown) {
                        // FIRST TIME TODAY: use time limit, then techniques every 3 min after

                        // Time limit reached for this session
                        if (timeElapsed >= timeLimitSeconds && !timeLimitReachedThisSession) {
                            timeLimitReachedThisSession = true
                            showFirstMessage()
                            markFirstMessageShownToday()
                        }

                        // Subsequent notifications every 3 minutes after time limit reached
                        if (timeLimitReachedThisSession) {
                            val timeAfterLimit = timeElapsed - timeLimitSeconds
                            if (timeAfterLimit > 0 && timeAfterLimit % SUBSEQUENT_INTERVAL == 0) {
                                showTechniqueMessage()
                            }
                        }
                    } else {
                        // SUBSEQUENT SESSIONS: ignore time limit, techniques at 3, 6, 9...
                        if (timeElapsed > 0 && timeElapsed % SUBSEQUENT_INTERVAL == 0) {
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
                            incrementBreaks()
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
        val startTime = endTime - 1000 * 60 // Query last minute

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
        val technique = getNextTechnique()
        incrementTechniques()
        // category is available here as technique.category if you want to log or display it
        showOverlayOrNotification(title, technique.subheading, technique.body)
    }

    private fun getNextTechnique(): Technique {
        // If all techniques have been shown, reshuffle
        if (remainingTechniques.isEmpty()) {
            remainingTechniques = techniques.indices.shuffled().toMutableList()
        }
        // Get and remove the next technique index
        val index = remainingTechniques.removeAt(0)
        return techniques[index]
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