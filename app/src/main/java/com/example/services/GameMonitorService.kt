package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.utils.RenameUtil

class GameMonitorService : Service() {
    private val checkInterval = 1000L
    private val TARGET_PKG = "com.dts.freefiremax"
    private val GRACE_PERIOD_MS = 180000L

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastQueryTime = System.currentTimeMillis() - 7200000L
    private var lastSeenForegroundTime = System.currentTimeMillis()
    private var currentForegroundPkg: String? = null

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            Thread {
                updateForegroundApp()
                val isGameActive = currentForegroundPkg == TARGET_PKG
                if (isGameActive) {
                    lastSeenForegroundTime = System.currentTimeMillis()
                }

                if (!isGameActive && (System.currentTimeMillis() - lastSeenForegroundTime) > GRACE_PERIOD_MS) {
                    if (RenameUtil.isModOn()) {
                        RenameUtil.turnOff(applicationContext)
                    }
                    isRunning = false
                    stopForeground(true)
                    stopSelf()
                } else {
                    handler.postDelayed(this, checkInterval)
                }
            }.start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "monitor_channel",
                "Game Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastSeenForegroundTime = System.currentTimeMillis()
        val notif = NotificationCompat.Builder(this, "monitor_channel")
            .setContentTitle("Xcel-2 Game Booster")
            .setContentText("Game monitor & optimizer active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2001, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(2001, notif)
        }

        if (!isRunning) {
            isRunning = true
            handler.post(monitorRunnable)
        }
        return START_STICKY
    }

    private fun updateForegroundApp() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQueryTime, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentForegroundPkg = event.packageName
            }
        }
        lastQueryTime = now
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
