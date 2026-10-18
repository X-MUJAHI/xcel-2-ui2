package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.utils.AlarmReceiver
import com.example.utils.RenameUtil
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

class GameMonitorService : Service() {

    companion object {
        private const val TAG = "GameMonitorService"
        const val PKG_FF_MAX = "com.dts.freefiremax"
        const val PKG_FF_TH = "com.dts.freefireth"

        const val PREF_KEY_FFTH_ALLOWED = "ffth_opened_via_app"
        const val PREF_KEY_FFTH_TIME = "ffth_opened_timestamp"
        const val FIVE_MINUTES_GRACE_MS = 5 * 60 * 1000L
    }

    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L

    private var lastQueryTime = System.currentTimeMillis() - 1000 * 60 * 60 * 2 // 2 hours ago
    private var currentForegroundPkg: String? = null
    private var lastSeenForegroundTime = System.currentTimeMillis()
    private val INACTIVITY_THRESHOLD_MS = 20 * 60 * 1000L // 20 minutes

    private var isFfThActiveSession = false
    private var lastBlockToastTime = 0L

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("monitor_channel", "Game Monitor", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastSeenForegroundTime = System.currentTimeMillis()
        val notification = NotificationCompat.Builder(this, "monitor_channel")
            .setContentTitle("xcel2 is working")
            .setContentText("Mod is active while you play")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(2001, notification)

        if (!isRunning) {
            isRunning = true
            handler.post(monitorRunnable)
        }
        return START_STICKY
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            Thread {
                updateForegroundApp()
                val isFfMax = (currentForegroundPkg == PKG_FF_MAX)
                val isFfTh = (currentForegroundPkg == PKG_FF_TH)
                val isGameForeground = isFfMax || isFfTh

                val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

                if (isGameForeground) {
                    lastSeenForegroundTime = System.currentTimeMillis()
                    // Track active time
                    val activeTime = prefs.getLong("game_active_time_ms", 0L)
                    prefs.edit().putLong("game_active_time_ms", activeTime + checkInterval).apply()
                }

                // Check manual opening of com.dts.freefireth
                if (isFfTh) {
                    // Cancel any pending 5-minute suspend alarm while actively in game
                    AlarmReceiver.cancelSuspendAlarm(applicationContext)

                    val isAllowed = prefs.getBoolean(PREF_KEY_FFTH_ALLOWED, false)
                    val openTime = prefs.getLong(PREF_KEY_FFTH_TIME, 0L)
                    val isFreshLaunch = (System.currentTimeMillis() - openTime) < 45_000L

                    if (isAllowed || isFfThActiveSession || isFreshLaunch) {
                        // Legitimate launch via "Open Game" in xcel2
                        isFfThActiveSession = true
                    } else {
                        // Unauthorized manual launch of com.dts.freefireth!
                        Log.w(TAG, "Manual launch of $PKG_FF_TH detected without pressing 'Open Game'. Forcing back and suspending.")

                        val now = System.currentTimeMillis()
                        if (now - lastBlockToastTime > 3000L) {
                            lastBlockToastTime = now
                            handler.post {
                                Toast.makeText(
                                    applicationContext,
                                    "Direct opening of Free Fire is blocked!\nPlease use 'OPEN GAME' inside xcel2.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        // 1. Force back by launching Home
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            startActivity(homeIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting home intent: ${e.message}")
                        }

                        // 2. Bring xcel2 back to foreground
                        val appIntent = Intent(applicationContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("blocked_manual_ffth", true)
                        }
                        try {
                            startActivity(appIntent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error bringing MainActivity to front: ${e.message}")
                        }

                        // 3. Immediately re-suspend and force-stop via Shizuku
                        if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
                            RenameUtil.executeShizukuCommand("am force-stop $PKG_FF_TH")
                            RenameUtil.suspendPackage(PKG_FF_TH)
                            RenameUtil.executeShizukuCommand("input keyevent KEYCODE_BACK")
                        }
                    }
                } else {
                    // Left Free Fire TH
                    if (isFfThActiveSession) {
                        isFfThActiveSession = false
                        // Start the 5-minute grace period alarm before suspending
                        AlarmReceiver.scheduleSuspendAlarm(applicationContext, FIVE_MINUTES_GRACE_MS)
                    }
                }

                if (!isGameForeground && (System.currentTimeMillis() - lastSeenForegroundTime > INACTIVITY_THRESHOLD_MS)) {
                    // Turn OFF - Normal users only
                    val userType = prefs.getString(MainActivity.KEY_USER_TYPE, "NORMAL")
                    if (userType == "NORMAL" && RenameUtil.isModOn()) {
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

    private fun updateForegroundApp() {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val events = usm.queryEvents(lastQueryTime, time)
            while (events.hasNextEvent()) {
                val ev = UsageEvents.Event()
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    currentForegroundPkg = ev.packageName
                }
            }
            lastQueryTime = time
        } catch (e: Exception) {
            Log.e(TAG, "Error querying usage events: ${e.message}")
        }
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
